package com.asbg.awsstudymcp.search.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;

/**
 * 연결은 받지만 응답하지 않는 로컬 서버로 호출 전체 제한시간을 확인한다.
 */
class GeminiEmbeddingTimeoutTest {

	private ServerSocket server;

	private final List<Socket> accepted = new ArrayList<>();

	private Thread acceptor;

	@BeforeEach
	void startSilentServer() throws IOException {
		server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
		acceptor = Thread.ofVirtual().start(() -> {
			try {
				while (true) {
					Socket socket = server.accept();
					synchronized (accepted) {
						accepted.add(socket);
					}
				}
			}
			catch (IOException e) {
				// 서버를 닫으면 끝난다.
			}
		});
	}

	@AfterEach
	void stopServer() throws Exception {
		server.close();
		acceptor.join(Duration.ofSeconds(1));
		synchronized (accepted) {
			for (Socket socket : accepted) {
				socket.close();
			}
		}
	}

	@Test
	void 응답이_없으면_제한시간에_요청을_끊고_재시도_가능_오류() {
		var properties = new GeminiEmbeddingProperties("dummy-key", "gemini-embedding-2", 768,
				Duration.ofMillis(300));
		HttpOptions.Builder local = HttpOptions.builder().baseUrl("http://127.0.0.1:" + server.getLocalPort());

		try (Client client = GeminiEmbeddingConfig.createClient(properties, local)) {
			var provider = new GeminiEmbeddingProvider(client.models::embedContent, "gemini-embedding-2", 768);

			long start = System.nanoTime();
			assertThatThrownBy(() -> provider.embedQuery("q")).isInstanceOfSatisfying(EmbeddingException.class,
					e -> assertThat(e.isRetryable()).isTrue());
			assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(3));
		}
	}

}
