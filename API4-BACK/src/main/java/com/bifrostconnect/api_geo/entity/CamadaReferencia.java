package com.bifrostconnect.api_geo.entity;

import java.time.LocalDate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Geometry;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "camada_referencia")
@Getter
@Setter
public class CamadaReferencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fonte", nullable = false, length = 50)
    private String fonte;

    @Column(name = "nome_camada", nullable = false, length = 100)
    private String nomeCamada;

    @Column(name = "identificador_origem", length = 100)
    private String identificadorOrigem;

    @Column(name = "geom", nullable = false, columnDefinition = "geometry(Geometry, 4674)")
    private Geometry geom;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "atributos", columnDefinition = "jsonb")
    private String atributos;

    @Column(name = "data_referencia")
    private LocalDate dataReferencia;
}