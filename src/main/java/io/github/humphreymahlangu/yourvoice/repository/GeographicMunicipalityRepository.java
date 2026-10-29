package io.github.humphreymahlangu.yourvoice.repository;

import io.github.humphreymahlangu.yourvoice.entity.GeographicMunicipality;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
public interface GeographicMunicipalityRepository extends JpaRepository<GeographicMunicipality, UUID> {
    List<GeographicMunicipality> findAllByOrderByNameAsc();
    Optional<GeographicMunicipality> findByProvinceIdAndNormalisedName(UUID provinceId, String normalisedName);
    @Modifying(flushAutomatically = true)
    @Query(value = "insert into geographic_municipalities(id,province_id,name,normalised_name) values (:id,:provinceId,:name,:normalisedName) on conflict(province_id,normalised_name) do nothing", nativeQuery = true)
    int insertIfAbsent(UUID id, UUID provinceId, String name, String normalisedName);
}
