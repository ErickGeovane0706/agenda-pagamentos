package com.agenda;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Sobe o contexto Spring inteiro contra um Postgres descartável. É o único teste
 * que faz isso: os demais são unitários e, por isso, não enxergam falha de wiring.
 * <p>
 * Existe por causa de um bug real: o {@code AsaasClient} ganhou um segundo
 * construtor (para teste) sem {@code @Autowired} no principal, deixando o Spring
 * sem saber qual usar. A suíte inteira passou verde e a aplicação não subia — só
 * apareceu ao rodar a aplicação de verdade. Um teste que instancia o contexto
 * transforma esse tipo de erro em build vermelho.
 * <p>
 * Como usa o Postgres real, cobre de brinde o que o H2 não cobriria: as migrations
 * Flyway (V1 em diante) rodam de fato e o {@code ddl-auto: validate} confere as
 * entidades JPA contra o schema que elas produzem.
 * <p>
 * Requer Docker rodando. Não faz rede além do container — as credenciais do perfil
 * {@code test} são de fachada e nenhum bean chama API externa ao subir.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class ApplicationContextSmokeTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            // Acompanha a major da produção; ver docs/adr/0002-schema-so-por-migration.md.
            new PostgreSQLContainer<>("postgres:18-alpine");

    /**
     * Sem corpo de propósito: a asserção é o próprio contexto subir. Se qualquer
     * bean não puder ser construído, o @SpringBootTest falha antes daqui.
     */
    @Test
    void contextoSobe() {
    }
}
