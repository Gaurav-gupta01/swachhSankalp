package CleanVision.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;

import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;

/**
 * Boots Hibernate metadata without a database connection so that broken entity
 * mappings are caught by the build instead of on first launch.
 */
class EntityMappingTest {

    private static final List<Class<?>> ENTITIES = List.of(
            User.class, Report.class, Team.class, Assignment.class, StatusHistory.class);

    @Test
    void allEntitiesProduceValidMappings() {
        assertThatCode(() -> {
            StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                    .applySetting("hibernate.dialect", "org.hibernate.dialect.MySQLDialect")
                    .applySetting("hibernate.hbm2ddl.auto", "none")
                    .build();
            try {
                MetadataSources sources = new MetadataSources(registry);
                ENTITIES.forEach(sources::addAnnotatedClass);
                Metadata metadata = sources.buildMetadata();

                assertThat(metadata.getEntityBinding("CleanVision.domain.Report")).isNotNull();
                assertThat(metadata.getEntityBinding("CleanVision.domain.User")).isNotNull();
                assertThat(metadata.getEntityBinding("CleanVision.domain.Team")).isNotNull();
                assertThat(metadata.getEntityBinding("CleanVision.domain.Assignment")).isNotNull();
                assertThat(metadata.getEntityBinding("CleanVision.domain.StatusHistory")).isNotNull();
                assertThat(metadata.getEntityBinding("CleanVision.domain.Report").getTable().getName())
                        .isEqualToIgnoringCase("reports");
            } finally {
                StandardServiceRegistryBuilder.destroy(registry);
            }
        }).doesNotThrowAnyException();
    }
}
