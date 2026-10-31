package com.bifrostconnect.api_geo.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bifrostconnect.api_geo.entity.ArquivoProcessado;

public interface AnaliseEspacialRepository extends JpaRepository<ArquivoProcessado, Long> {

    @Query(value = "SELECT COUNT(*) > 0 FROM arquivo_processado WHERE ST_Intersects(geometria, ST_GeomFromText(:wktGeom, 4674)) AND processo_id <> :processoId", nativeQuery = true)
    boolean existeSobreposicao(@Param("wktGeom") String wktGeom, @Param("processoId") Long processoId);
}