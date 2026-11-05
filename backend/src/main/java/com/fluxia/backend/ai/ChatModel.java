package com.fluxia.backend.ai;

import java.util.List;

/**
 * Un modelo de lenguaje que responde una conversacion.
 *
 * <p>Separa al asistente del proveedor: {@link AiChatService} arma la
 * conversacion y decide que hacer si un modelo falla; cada implementacion
 * solo sabe hablar con su API. Cambiar de proveedor no toca las reglas del
 * asistente ni el contexto financiero.
 */
public interface ChatModel {

    /**
     * Le pregunta al modelo indicado.
     *
     * @param model        identificador del modelo en el proveedor
     * @param systemPrompt instrucciones del asistente
     * @param conversation turnos en orden, con rol {@code user} o
     *                     {@code assistant}; el ultimo es del usuario
     * @return la respuesta; con texto vacio si el modelo no respondio
     * @throws ChatModelException si el proveedor no atendio el pedido
     */
    ChatReply reply(String model, String systemPrompt, List<AiDtos.Turn> conversation);
}
