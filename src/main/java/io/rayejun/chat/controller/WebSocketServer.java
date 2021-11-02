package io.rayejun.chat.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import io.rayejun.chat.utils.JacksonUtil;
import org.springframework.stereotype.Component;

import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ServerEndpoint("/websocket")
public class WebSocketServer {

    private final static Map<String, List<Session>> sessions = new ConcurrentHashMap<>();

    // 当前在线连接数
    private static int onlineCount = 0;

    /**
     * 连接建立成功调用的方法
     */
    @OnOpen
    public void onOpen(Session session) throws IOException {
        // 在线数加1
        addOnlineCount();
        String roomId = getSessionParameter("roomId", session);
        addSession(roomId, session);

        List<Session> sessionList = getSession(roomId);
        if (!sessionList.isEmpty()) {
            List<Session> sessions = getSession(roomId);
            Map<String, Object> json = new HashMap<>();
            json.put("type", "online");
            json.put("content", new HashMap<String, Object>() {{
                put("online", onlineCount);
                put("ip", getSessionParameter("ip", session));
            }});
            for (Session session1 : sessions) {
                if (session1.isOpen()) {
                    // 发送通知在线人数消息
                    session1.getBasicRemote().sendText(JacksonUtil.toJson(json));
                }
            }
        }
        System.out.println("有新连接加入！roomId为：" + roomId + "   ID是：" + session.getId() + "    当前在线人数为：" + getOnlineCount());
    }

    /**
     * 收到客户端消息后调用的方法
     */
    @OnMessage
    public void onMessage(String message, Session session) throws IOException {
        Map<String, Object> json = JacksonUtil.parse(message, new TypeReference<Map<String, Object>>() {
        });
        // type为msg是消息
        if (json.get("type").equals("msg")) {
            List<Session> sessionList = getSession(json.get("roomId").toString());
            if (!sessionList.isEmpty()) {
                json.put("time", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                List<Session> sessions = getSession(json.get("roomId").toString());
                for (Session session1 : sessions) {
                    if (session1.isOpen()) {
                        session1.getBasicRemote().sendText(JacksonUtil.toJson(json));
                    }
                }
            }
        }
        System.out.println("来自客户端的消息：" + message + "   ID是：" + session.getId());
    }

    /**
     * 连接关闭调用的方法
     */
    @OnClose
    public void onClose(Session session) {
        // 在线数减1
        subOnlineCount();
        String roomId = getSessionParameter("roomId", session);
        removeSession(roomId, session);
        System.out.println("有一连接关闭！ID是：" + session.getId() + "   当前在线人数为：" + getOnlineCount());

        try {
            session.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 发生错误
     *
     * @param session
     * @param error
     */
    @OnError
    public void onError(Session session, Throwable error) {
        // 在线数减1
        subOnlineCount();
        String roomId = getSessionParameter("roomId", session);
        removeSession(roomId, session);
        System.out.println("发生错误！发生时间：" + System.currentTimeMillis() + "  ID是：" + session.getId());

        try {
            session.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        error.printStackTrace();
    }

    public static synchronized int getOnlineCount() {
        return onlineCount;
    }

    public static synchronized void addOnlineCount() {
        WebSocketServer.onlineCount++;
    }

    public static synchronized void subOnlineCount() {
        if (WebSocketServer.onlineCount > 0) {
            WebSocketServer.onlineCount--;
        }
    }

    public String getSessionParameter(String key, Session session) {
        Map<String, List<String>> params = session.getRequestParameterMap();
        if (params.get(key) == null) return null;
        return params.get(key).get(0);
    }

    public List<Session> getSession(String roomId) {
        return sessions.getOrDefault(roomId, null);
    }

    public void addSession(String roomId, Session session) {
        List<Session> sessionList = getSession(roomId);
        if (sessionList == null) {
            sessionList = new ArrayList<>();
        } else {
            sessionList.remove(session);
        }
        sessionList.add(session);
        sessions.put(roomId, sessionList);
    }

    public void removeSession(String roomId, Session session) {
        if (sessions.containsKey(roomId)) {
            List<Session> sessionList = getSession(roomId);
            if (sessionList == null) {
                sessions.remove(roomId);
            } else {
                sessionList.remove(session);
                if (sessionList.isEmpty()) {
                    sessions.remove(roomId);
                } else {
                    sessions.put(roomId, sessionList);
                }
            }
        }
    }
}
