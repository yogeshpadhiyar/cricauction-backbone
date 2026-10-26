package com.cricauction.modules.team.repository;

import com.cricauction.modules.team.entity.SquadMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SquadMemberRepository extends JpaRepository<SquadMember, Long> {

    List<SquadMember> findByTeamIdOrderByIdAsc(Long teamId);

    long countByTeamId(Long teamId);

    Optional<SquadMember> findByTeamIdAndIsCaptainTrue(Long teamId);

    Optional<SquadMember> findByTeamIdAndPlayerUserId(Long teamId, Long playerUserId);
}
