package com.cricauction.modules.match.repository;

import com.cricauction.modules.match.entity.MatchBall;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MatchBallRepository extends JpaRepository<MatchBall, Long> {

    Optional<MatchBall> findByMatchIdAndInningsAndOverNumberAndBallNumber(
            Long matchId, Integer innings, Integer overNumber, Integer ballNumber);
}

