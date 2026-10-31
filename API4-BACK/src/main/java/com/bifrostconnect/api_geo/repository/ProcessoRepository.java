package com.bifrostconnect.api_geo.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.bifrostconnect.api_geo.entity.Processo;

public interface ProcessoRepository extends JpaRepository<Processo, Long> {
    @Query(value = "SELECT id FROM etapa WHERE nome = :nome", nativeQuery = true)
    Optional<Long> buscarEtapaIdPorNome(@Param("nome") String nome);

    @Query(value = "SELECT id FROM situacao WHERE nome = :nome", nativeQuery = true)
    Optional<Long> buscarSituacaoIdPorNome(@Param("nome") String nome);
}
