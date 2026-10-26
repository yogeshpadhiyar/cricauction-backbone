package com.cricauction.modules.tournament.repository;

import com.cricauction.modules.tournament.entity.Tournament;
import com.cricauction.modules.tournament.entity.enums.TournamentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TournamentRepository extends JpaRepository<Tournament, Long> {

    Optional<Tournament> findByPublicId(String publicId);

    Optional<Tournament> findBySlug(String slug);

    boolean existsBySlug(String slug);

    boolean existsByPublicId(String publicId);

    List<Tournament> findByOrganizerIdOrderByCreatedAtDesc(Long organizerId);

    List<Tournament> findByOrganizerIdAndStatusOrderByCreatedAtDesc(Long organizerId, TournamentStatus status);

    @Query("SELECT t FROM Tournament t LEFT JOIN FETCH t.settings WHERE t.publicId = :idOrSlug OR t.slug = :idOrSlug")
    Optional<Tournament> findByPublicIdOrSlugWithSettings(@Param("idOrSlug") String idOrSlug);

    @Query("SELECT t FROM Tournament t LEFT JOIN FETCH t.settings WHERE t.slug = :slug")
    Optional<Tournament> findBySlugWithSettings(@Param("slug") String slug);

    @Query("SELECT t FROM Tournament t LEFT JOIN FETCH t.settings WHERE t.id = :id")
    Optional<Tournament> findByIdWithSettings(@Param("id") Long id);

    long countByOrganizerId(Long organizerId);
}
