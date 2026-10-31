package com.bifrostconnect.api_geo.service.conversor;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Interface estratégica para conversores de diferentes formatos de arquivos geoespaciais
 * (GeoJSON, Shapefile ZIP, GeoPackage, KML) para o formato padrão GeoJSON utilizado na esteira de processamento.
 */
public interface ConversorArquivoGeoespacial {

    /**
     * Verifica se o conversor suporta o formato com base na extensão e/ou inspeção do arquivo.
     *
     * @param extensao extensão em minúsculas (sem o ponto, ex: "geojson", "zip", "gpkg", "kml", "kmz")
     * @param arquivo caminho do arquivo físico na Zona Bruta
     * @return true se o conversor for capaz de processar o arquivo
     */
    boolean suporta(String extensao, Path arquivo);

    /**
     * Converte o arquivo de origem para um arquivo GeoJSON padronizado temporário.
     * Caso o arquivo já seja um GeoJSON simples, pode retornar o próprio caminho.
     *
     * @param arquivoOrigem caminho do arquivo original na Zona Bruta
     * @return Path do arquivo GeoJSON pronto para consumo
     * @throws IOException em caso de erro de leitura ou escrita
     * @throws IllegalArgumentException se o arquivo for inválido ou corrompido
     */
    Path converterParaGeoJson(Path arquivoOrigem) throws IOException;
}
