package com.example.editor.system;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Comprueba los endpoints HTTP básicos de la aplicación utilizando MockMvc.
 *
 * Las pruebas se ejecutan sin abrir un puerto HTTP real.
 */
@SpringBootTest(properties = {"dsl.storage.create-directories=false", "hydra.instance-lock.enabled=false"})
@AutoConfigureMockMvc
class SystemEndpointsTests {

    @Autowired
    private MockMvc mockMvc;

    /**
     * Descripción:
     * Comprueba que el endpoint devuelve el nombre y el estado esperados.
     *
     * Entrada:
     * No recibe parámetros.
     *
     * Salida:
     * El test finaliza correctamente cuando la respuesta contiene editor y UP.
     *
     * @throws Exception cuando MockMvc no puede ejecutar la petición.
     */
    @Test
    void systemStatusReturnsApplicationAndStatus() throws Exception {
        mockMvc.perform(get("/api/system/status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.application").value("HYDRA"))
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.product").value("HYDRA"))
            .andExpect(jsonPath("$.version").doesNotExist());
    }

    /**
     * Descripción:
     * Comprueba que el endpoint responde utilizando el formato JSON.
     *
     * Entrada:
     * No recibe parámetros.
     *
     * Salida:
     * El test finaliza correctamente cuando el Content-Type es JSON.
     *
     * @throws Exception cuando MockMvc no puede ejecutar la petición.
     */
    @Test
    void systemStatusUsesJsonContentType() throws Exception {
        mockMvc.perform(get("/api/system/status"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    /**
     * Descripción:
     * Comprueba que Spring Boot Actuator considera saludable la aplicación.
     *
     * Entrada:
     * No recibe parámetros.
     *
     * Salida:
     * El test finaliza correctamente cuando Actuator devuelve el estado UP.
     *
     * @throws Exception cuando MockMvc no puede ejecutar la petición.
     */
    @Test
    void actuatorHealthIsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
    }
}