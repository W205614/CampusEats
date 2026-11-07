package com.campuseats.websocket;

import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.*;

@Configuration
@ConditionalOnProperty(name = "campus.cluster.enabled", havingValue = "true")
public class ClusterSocketConfiguration {
  @Bean
  RedisMessageListenerContainer orderNotifications(
      @Qualifier("redisConnectionFactory") RedisConnectionFactory connection, OrderSocket socket) {
    var listener = new RedisMessageListenerContainer();
    listener.setConnectionFactory(connection);
    listener.addMessageListener(
        (message, channel) ->
            socket.localPublish(new String(message.getBody(), StandardCharsets.UTF_8)),
        new ChannelTopic("campus:orders:v1"));
    return listener;
  }
}
