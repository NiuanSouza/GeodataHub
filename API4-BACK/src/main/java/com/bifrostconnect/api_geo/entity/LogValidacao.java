package com.bifrostconnect.api_geo.entity;

import jakarta.persistence.*;
import lombok.*;
import org.locationtech.jts.geom.Geometry;

import java.time.LocalDateTime;

@Entity
@Table(name = "log_validacao")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LogValidacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "processo_id", nullable = false)
    private Processo processo;

    // Sem @ManyToOne para OcorrenciaValidacao: essa entidade ainda não
    // existe no projeto (só processo/arquivo_original foram mapeados até
    // agora). Se/quando ela for criada, troque por @ManyToOne + @JoinColumn.
    @Column(name = "ocorrencia_validacao_id")
    private Long ocorrenciaValidacaoId;

    @Column(name = "identificador_feicao", length = 100)
    private String identificadorFeicao;

    @Column(name = "geom", columnDefinition = "geometry")
    private Geometry geom;

    @Column(name = "srid_origem")
    private Integer sridOrigem;

    @Column(name = "geom_valida")
    private Boolean geomValida;

    @Column(name = "motivo_invalidez")
    private String motivoInvalidez;

    @Column(name = "geom_bruta")
    private String geomBruta;

    @Column(name = "severidade", nullable = false, length = 20)
    private String severidade;

    @Column(name = "mensagem", nullable = false)
    private String mensagem;

    @Column(name = "data_deteccao", insertable = false, updatable = false)
    private LocalDateTime dataDeteccao;
}