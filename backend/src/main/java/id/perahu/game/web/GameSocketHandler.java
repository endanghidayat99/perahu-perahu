package id.perahu.game.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import id.perahu.game.room.RoomService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Jembatan WebSocket <-> RoomService. Satu endpoint: /ws, pesan JSON. */
@Component
public class GameSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(GameSocketHandler.class);

    private final ObjectMapper mapper;
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final RoomService rooms;

    public GameSocketHandler(ObjectMapper mapper,
                             @Value("${game.min-players:4}") int minPlayers,
                             @Value("${game.max-players:5}") int maxPlayers,
                             @Value("${game.offline-autoplay-seconds:45}") long autoPlaySeconds,
                             @Value("${game.lobby-offline-kick-seconds:120}") long lobbyKickSeconds,
                             @Value("${game.idle-room-minutes:30}") long idleRoomMinutes,
                             @Value("${game.turn-timeout-seconds:0}") long turnTimeoutSeconds,
                             @Value("${game.bot-delay-millis:1200}") long botDelayMillis) {
        this.mapper = mapper;
        this.rooms = new RoomService(this::send, new RoomService.Settings(
                minPlayers, maxPlayers, autoPlaySeconds * 1000, lobbyKickSeconds * 1000, idleRoomMinutes * 60_000,
                turnTimeoutSeconds * 1000, botDelayMillis));
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.put(session.getId(), new ConcurrentWebSocketSessionDecorator(session, 5_000, 512 * 1024));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        Map<String, Object> msg;
        try {
            msg = mapper.readValue(message.getPayload(), new TypeReference<Map<String, Object>>() {});
        } catch (IOException e) {
            send(session.getId(), Map.of("type", "error", "code", "BAD_JSON", "message", "Format pesan salah."));
            return;
        }
        try {
            rooms.handle(session.getId(), msg, System.currentTimeMillis());
        } catch (RuntimeException e) {
            log.error("Gagal memproses pesan {}", msg, e);
            send(session.getId(), Map.of("type", "error", "code", "SERVER_ERROR", "message", "Terjadi kesalahan di server."));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
        rooms.disconnected(session.getId(), System.currentTimeMillis());
    }

    @Scheduled(fixedDelay = 1_000)
    public void tick() {
        rooms.tick(System.currentTimeMillis());
    }

    private void send(String sessionId, Map<String, Object> message) {
        WebSocketSession s = sessions.get(sessionId);
        if (s == null || !s.isOpen()) return;
        try {
            s.sendMessage(new TextMessage(mapper.writeValueAsString(message)));
        } catch (IOException | IllegalStateException e) {
            log.debug("Gagal kirim ke {}: {}", sessionId, e.getMessage());
        }
    }
}
