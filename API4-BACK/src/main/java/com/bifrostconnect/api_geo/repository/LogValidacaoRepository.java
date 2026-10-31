package com.bifrostconnect.api_geo.repository;

import com.bifrostconnect.api_geo.entity.LogValidacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LogValidacaoRepository extends JpaRepository<LogValidacao, Long> {

    @Query(value = "SELECT ST_Area(geom) / 10000.0 FROM log_validacao WHERE id = :id", nativeQuery = true)
    Double calcularAreaEmHectares(@Param("id") Long id);

    // Método adicionado para a Tarefa 15: buscar os logs da quarentena da carga
    List<LogValidacao> findByProcessoId(Long processoId);

}