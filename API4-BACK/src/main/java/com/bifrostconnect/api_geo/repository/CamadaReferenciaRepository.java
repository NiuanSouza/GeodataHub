package com.bifrostconnect.api_geo.repository;

import com.bifrostconnect.api_geo.entity.CamadaReferencia;

import java.util.List;

import org.locationtech.jts.geom.Geometry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CamadaReferenciaRepository extends JpaRepository<CamadaReferencia, Long> {

    /**
     * Busca sobreposições espaciais entre a geometria do imóvel e as camadas de referência filtradas por fonte.
     */
    @Query(value = """
        SELECT cr.* FROM camada_referencia cr
        WHERE cr.fonte IN (:fontes)
        AND ST_Intersects(cr.geom, :geometriaImovel) = true
        """, nativeQuery = true)
    List<CamadaReferencia> buscarSobreposicoes(
        @Param("fontes") List<String> fontes,
        @Param("geometriaImovel") Geometry geometriaImovel
    );

    /**
     * Identifica o município ou bioma em que o imóvel está contido.
     */
    @Query(value = """
        SELECT cr.* FROM camada_referencia cr
        WHERE cr.fonte = :fonte
        AND ST_Intersects(cr.geom, :geometriaImovel) = true
        """, nativeQuery = true)
    List<CamadaReferencia> buscarInterseccaoPorFonte(
        @Param("fonte") String fonte,
        @Param("geometriaImovel") Geometry geometriaImovel
    );

    /**
     * Trava de Blindagem (Tarefa 15): Retorna apenas camadas com etapa 'Publicacao', 
     * situação 'Concluida' e versao_fechada = true para o analista.
     */
    @Query(value = """
        SELECT cr.* FROM camada_referencia cr
        WHERE cr.etapa_atual = 'Publicacao'
          AND cr.situacao = 'Concluida'
          AND cr.versao_fechada = true
          AND (:conjunto IS NULL OR cr.conjunto = :conjunto)
          AND (:ano IS NULL OR cr.ano = :ano)
        """, nativeQuery = true)
    List<CamadaReferencia> buscarCamadasPublicadasAnalista(
        @Param("conjunto") String conjunto,
        @Param("ano") Integer ano
    );

    /**
     * Controle de Volume de Dados / Payload (Tarefa 17): Conta o número de feições 
     * publicadas que atendem aos filtros antes de carregar o payload completo.
     */
    @Query(value = """
        SELECT COUNT(cr.id) FROM camada_referencia cr
        WHERE cr.etapa_atual = 'Publicacao'
          AND cr.situacao = 'Concluida'
          AND cr.versao_fechada = true
          AND (:conjunto IS NULL OR cr.conjunto = :conjunto)
          AND (:ano IS NULL OR cr.ano = :ano)
        """, nativeQuery = true)
    long contarCamadasPublicadasAnalista(
        @Param("conjunto") String conjunto,
        @Param("ano") Integer ano
    );

    @Query(value = """
        SELECT CASE WHEN COUNT(cr.id) > 0 THEN true ELSE false END 
        FROM camada_referencia cr 
        WHERE cr.identificador IN (:identificadores)
        """, nativeQuery = true)
    boolean existemIdentificadoresDuplicados(@Param("identificadores") List<String> identificadores);

}