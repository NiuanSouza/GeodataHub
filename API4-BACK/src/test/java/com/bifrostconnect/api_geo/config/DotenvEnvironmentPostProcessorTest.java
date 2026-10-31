package com.bifrostconnect.api_geo.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DotenvEnvironmentPostProcessorTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Deve ler variáveis de ambiente de um arquivo .env com comentários e aspas")
    void deveLerVariaveisDeArquivoEnv() throws IOException {
        File envFile = tempDir.resolve(".env").toFile();
        try (FileWriter writer = new FileWriter(envFile)) {
            writer.write("# Comentário de cabeçalho\n");
            writer.write("DB_HOST=127.0.0.1\n");
            writer.write("DB_PORT=5433\n");
            writer.write("DB_NAME=sistema_teste\n");
            writer.write("DB_USER=\"admin\"\n");
            writer.write("DB_PASSWORD='minhasenha'\n");
            writer.write("PORT=9090 # Porta do servidor\n");
            writer.write("export CUSTOM_VAR=valor_customizado\n");
        }

        Map<String, Object> map = DotenvEnvironmentPostProcessor.loadEnvFile(envFile);

        assertEquals("127.0.0.1", map.get("DB_HOST"));
        assertEquals("5433", map.get("DB_PORT"));
        assertEquals("sistema_teste", map.get("DB_NAME"));
        assertEquals("admin", map.get("DB_USER"));
        assertEquals("minhasenha", map.get("DB_PASSWORD"));
        assertEquals("9090", map.get("PORT"));
        assertEquals("9090", map.get("server.port"));
        assertEquals("valor_customizado", map.get("CUSTOM_VAR"));
        assertEquals("jdbc:postgresql://127.0.0.1:5433/sistema_teste", map.get("spring.datasource.url"));
        assertEquals("admin", map.get("spring.datasource.username"));
        assertEquals("minhasenha", map.get("spring.datasource.password"));
    }

    @Test
    @DisplayName("Deve injetar as propriedades no ConfigurableEnvironment do Spring")
    void deveInjetarNoConfigurableEnvironment() throws IOException {
        File envFile = tempDir.resolve(".env").toFile();
        try (FileWriter writer = new FileWriter(envFile)) {
            writer.write("TEST_ENV_KEY=valor_injetado\n");
        }

        System.setProperty("dotenv.file", envFile.getAbsolutePath());
        try {
            ConfigurableEnvironment env = new StandardEnvironment();
            DotenvEnvironmentPostProcessor processor = new DotenvEnvironmentPostProcessor();
            processor.postProcessEnvironment(env, new SpringApplication(DotenvEnvironmentPostProcessorTest.class));

            assertEquals("valor_injetado", env.getProperty("TEST_ENV_KEY"));
            assertEquals("valor_injetado", System.getProperty("TEST_ENV_KEY"));
        } finally {
            System.clearProperty("dotenv.file");
            System.clearProperty("TEST_ENV_KEY");
        }
    }
}
