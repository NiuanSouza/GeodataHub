package com.bifrostconnect.api_geo.service;

import com.bifrostconnect.api_geo.entity.LogValidacao;
import com.bifrostconnect.api_geo.repository.LogValidacaoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExportacaoRejeicoesService {

    private final LogValidacaoRepository logValidacaoRepository;

    public byte[] exportarRejeicoesCsv(Long processoId) {
        List<LogValidacao> logs = logValidacaoRepository.findByProcessoId(processoId);

        if (logs.isEmpty()) {
            throw new IllegalArgumentException("Não existem rejeições na quarentena para este processo.");
        }

        StringWriter stringWriter = new StringWriter();
        
        // Adiciona o BOM (Byte Order Mark) do UTF-8 para o Excel ler acentos corretamente
        stringWriter.write('\ufeff'); 
        
        PrintWriter csvWriter = new PrintWriter(stringWriter);

        // Cabeçalho do CSV
        csvWriter.println("ID_Log;Data_Deteccao;Severidade;Mensagem_Erro");

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

        for (LogValidacao log : logs) {
            String data = log.getDataDeteccao() != null ? log.getDataDeteccao().format(formatter) : "";
            String severidade = log.getSeveridade() != null ? log.getSeveridade() : "";
            // Substitui possíveis pontos e vírgulas na mensagem para não quebrar as colunas do CSV
            String mensagem = log.getMensagem() != null ? log.getMensagem().replace(";", ",") : ""; 

            csvWriter.printf("%s;%s;%s;%s%n", log.getId(), data, severidade, mensagem);
        }

        // Converte a string final para bytes
        return stringWriter.toString().getBytes(StandardCharsets.UTF_8);
    }
}