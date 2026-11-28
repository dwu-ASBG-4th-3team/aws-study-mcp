"""Gemini 실제 임베딩 + S3 Vectors PoC (docs/search-poc.md 2절 6·7·9번).

AWS CloudShell(ap-northeast-2)에서 실행한다. API Key는 Secrets Manager에서 읽고 출력하지 않는다.

    python3 s3v_gemini_poc.py

확인하는 것
  1. CLF 샘플 질문 실제 임베딩: 768차원, 정규화(길이 1), 비슷한 질문이 더 가까운지
  2. S3 Vectors 저장 → userId 필터 검색 → 다른 사용자 기록 미노출 → domain 필터
  3. 삭제 후 재검색 시 제외, 없는 key 삭제 동작
끝나면 PoC 벡터(key가 poc-로 시작)를 모두 지운다.
"""

import json
import math
import sys
import time
import urllib.error
import urllib.request

import boto3

REGION = "ap-northeast-2"
SECRET_ID = "clfqa/dev/gemini-api-key"
BUCKET = "clf-qa-vectors-dev"
INDEX = "qa-index-gemini-v1"
MODEL = "gemini-embedding-2"
DIM = 768
ENDPOINT = f"https://generativelanguage.googleapis.com/v1beta/models/{MODEL}:embedContent"

USER_A = "poc-user-001"
USER_B = "poc-user-002"

# 저장 QA (embedDocument와 같은 형식)
DOCS = [
    {"key": "poc-qa-1", "userId": USER_A, "domain": "TECH", "title": "Cloud Technology and Services",
     "question": "S3와 EBS는 어떤 차이가 있나요?"},
    {"key": "poc-qa-2", "userId": USER_A, "domain": "SECURITY", "title": "Security and Compliance",
     "question": "IAM 역할과 IAM 사용자는 무엇이 다른가요?"},
    {"key": "poc-qa-3", "userId": USER_A, "domain": "BILLING", "title": "Billing, Pricing, and Support",
     "question": "AWS 요금이 일정 금액을 넘으면 알림을 받으려면 어떻게 하나요?"},
    {"key": "poc-qa-4", "userId": USER_A, "domain": "CONCEPTS", "title": "Cloud Concepts",
     "question": "리전과 가용 영역은 어떤 차이가 있나요?"},
    # 다른 사용자의 비슷한 질문: USER_A 검색에 나오면 안 된다
    {"key": "poc-qa-5", "userId": USER_B, "domain": "TECH", "title": "Cloud Technology and Services",
     "question": "EBS 볼륨이랑 S3 버킷 차이점 알려주세요"},
]

# 검색 질문 (embedQuery와 같은 형식): (질문, 기대하는 1위 key)
QUERIES = [
    ("S3 버킷이랑 EBS 볼륨 차이 알려줘", "poc-qa-1"),
    ("역할이랑 사용자 권한 차이가 뭐야?", "poc-qa-2"),
    ("비용이 예산을 넘으면 알려주는 기능이 있어?", "poc-qa-3"),
    ("AZ가 리전 안에 여러 개 있는 이유", "poc-qa-4"),
]
UNRELATED = "파스타 면을 알맞게 삶는 시간은?"

results = []


def check(name, ok, detail=""):
    results.append((name, ok, detail))
    print(f"[{'PASS' if ok else 'FAIL'}] {name}" + (f" - {detail}" if detail else ""))


def load_api_key():
    secret = boto3.client("secretsmanager", region_name=REGION).get_secret_value(SecretId=SECRET_ID)["SecretString"]
    secret = secret.strip()
    if secret.startswith("{"):
        data = json.loads(secret)
        for name in ("GEMINI_API_KEY", "apiKey", "api_key", "key"):
            if data.get(name):
                return data[name]
        sys.exit(f"시크릿 JSON에서 키 필드를 찾지 못함. 필드 이름: {list(data)}")
    return secret


def embed(api_key, text, attempt=1):
    body = json.dumps({"content": {"parts": [{"text": text}]}, "outputDimensionality": DIM}).encode()
    req = urllib.request.Request(ENDPOINT, data=body, method="POST",
                                 headers={"Content-Type": "application/json", "x-goog-api-key": api_key})
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            data = json.load(resp)
            embedding = data.get("embedding") or data["embeddings"][0]
            return embedding["values"]
    except urllib.error.HTTPError as e:
        if (e.code == 429 or e.code >= 500) and attempt < 3:
            print(f"  Gemini HTTP {e.code}, {20 * attempt}초 후 재시도")
            time.sleep(20 * attempt)
            return embed(api_key, text, attempt + 1)
        # 응답 본문에 키가 들어가지는 않지만 상태 코드만 보여준다.
        sys.exit(f"Gemini 호출 실패: HTTP {e.code} {e.reason}")


def embed_doc(api_key, doc):
    return embed(api_key, f"title: {doc['title']} | text: {doc['question']}")


def embed_query(api_key, query):
    return embed(api_key, f"task: search result | query: {query}")


def norm(v):
    return math.sqrt(sum(x * x for x in v))


def cosine(a, b):
    return sum(x * y for x, y in zip(a, b)) / (norm(a) * norm(b))


def query(s3v, vector, user_id, top_k=5, domain=None):
    conditions = [{"userId": {"$eq": user_id}}]
    if domain:
        conditions.append({"domain": {"$eq": domain}})
    flt = conditions[0] if len(conditions) == 1 else {"$and": conditions}
    resp = s3v.query_vectors(vectorBucketName=BUCKET, indexName=INDEX, queryVector={"float32": vector},
                             topK=top_k, filter=flt, returnDistance=True, returnMetadata=True)
    return [(v["key"], 1 - v["distance"], v.get("metadata", {}).get("userId")) for v in resp["vectors"]]


def main():
    s3v = boto3.client("s3vectors", region_name=REGION)
    api_key = load_api_key()
    print(f"API Key 로드 완료 (길이 {len(api_key)})\n")

    # 1. 실제 임베딩
    print("== 1. Gemini 실제 임베딩")
    doc_vecs = {d["key"]: embed_doc(api_key, d) for d in DOCS}
    query_vecs = [embed_query(api_key, q) for q, _ in QUERIES]
    unrelated_vec = embed_query(api_key, UNRELATED)

    dims = {len(v) for v in list(doc_vecs.values()) + query_vecs}
    check("차원 768", dims == {DIM}, f"실제 {sorted(dims)}")
    norms = [norm(v) for v in doc_vecs.values()]
    check("정규화 (길이 ≈ 1)", all(abs(n - 1) < 0.01 for n in norms), f"{min(norms):.4f} ~ {max(norms):.4f}")

    own_docs = {k: v for k, v in doc_vecs.items() if k != "poc-qa-5"}
    correct_sims = []
    for (q, expected), qv in zip(QUERIES, query_vecs):
        ranked = sorted(own_docs, key=lambda k: cosine(qv, own_docs[k]), reverse=True)
        correct_sims.append(cosine(qv, own_docs[expected]))
        check(f"메모리 검색 1위 = {expected}", ranked[0] == expected, f"'{q}' → {ranked[0]}")
    unrelated_max = max(cosine(unrelated_vec, v) for v in own_docs.values())
    print(f"  정답 유사도 최저 {min(correct_sims):.4f} / 관계없는 질문 최고 {unrelated_max:.4f}")
    check("관계없는 질문 < 정답 유사도", unrelated_max < min(correct_sims))

    try:
        run_s3_vectors(s3v, doc_vecs, query_vecs)
    finally:
        # 중간에 실패해도 PoC 벡터는 지운다.
        s3v.delete_vectors(vectorBucketName=BUCKET, indexName=INDEX, keys=[d["key"] for d in DOCS])
    left = query(s3v, query_vecs[0], USER_A) + query(s3v, query_vecs[0], USER_B)
    check("PoC 벡터 정리", not left, str(left))

    passed = sum(1 for _, ok, _ in results if ok)
    print(f"\n결과: {passed}/{len(results)} PASS")
    print(f"정답 유사도 최저 {min(correct_sims):.4f}, 관계없는 질문 최고 {unrelated_max:.4f}")
    sys.exit(0 if passed == len(results) else 1)


def run_s3_vectors(s3v, doc_vecs, query_vecs):
    # 2. S3 Vectors 저장·검색·격리
    print("\n== 2. S3 Vectors 저장·검색·사용자 격리")
    now = int(time.time())
    s3v.put_vectors(vectorBucketName=BUCKET, indexName=INDEX, vectors=[
        {"key": d["key"], "data": {"float32": doc_vecs[d["key"]]},
         "metadata": {"userId": d["userId"], "domain": d["domain"], "verification": "UNVERIFIED",
                      "createdAt": now, "questionPreview": d["question"][:200]}}
        for d in DOCS])
    check("put_vectors 5건", True)

    hits = query(s3v, query_vecs[0], USER_A)
    check("USER_A 검색 1위 = poc-qa-1", bool(hits) and hits[0][0] == "poc-qa-1",
          ", ".join(f"{k}({s:.3f})" for k, s, _ in hits))
    check("USER_A 결과에 USER_B 기록 없음", all(u == USER_A for _, _, u in hits) and "poc-qa-5" not in [k for k, _, _ in hits])

    hits_b = query(s3v, query_vecs[0], USER_B)
    check("USER_B 검색은 본인 기록만", [k for k, _, _ in hits_b] == ["poc-qa-5"], str([k for k, _, _ in hits_b]))

    hits_dom = query(s3v, query_vecs[0], USER_A, domain="SECURITY")
    check("domain 필터 (SECURITY만)", [k for k, _, _ in hits_dom] == ["poc-qa-2"], str([k for k, _, _ in hits_dom]))

    # 3. 삭제
    print("\n== 3. 삭제")
    s3v.delete_vectors(vectorBucketName=BUCKET, indexName=INDEX, keys=["poc-qa-1"])
    hits = query(s3v, query_vecs[0], USER_A)
    check("삭제 후 재검색 시 poc-qa-1 제외", "poc-qa-1" not in [k for k, _, _ in hits], str([k for k, _, _ in hits]))
    try:
        s3v.delete_vectors(vectorBucketName=BUCKET, indexName=INDEX, keys=["poc-qa-does-not-exist"])
        check("없는 key 삭제는 오류 없이 성공", True)
    except Exception as e:  # noqa: BLE001 - 동작 확인이 목적
        check("없는 key 삭제는 오류 없이 성공", False, type(e).__name__)


if __name__ == "__main__":
    main()
