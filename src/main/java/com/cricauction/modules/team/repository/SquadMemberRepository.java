package com.cricauction.modules.team.repository;

import com.cricauction.modules.team.entity.SquadMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SquadMemberRepository extends JpaRepository<SquadMember, Long> {

    List<SquadMember> findByTeamIdOrderByIdAsc(Long teamId);

    long countByTeamId(Long teamId);

    Optional<SquadMember> findByTeamIdAndIsCaptainTrue(Long teamId);

    Optional<SquadMember> findByTeamIdAndPlayerUserId(Long teamId, Long playerUserId);

    @Query("SELECT sm.team.id AS teamId, COUNT(sm.id) AS squadCount " +
           "FROM SquadMember sm WHERE sm.team.id IN :teamIds GROUP BY sm.team.id")
    List<TeamSquadCountProjection> countSquadMembersByTeamIds(@Param("teamIds") Collection<Long> teamIds);
}
