package com.kvs.admission.repo;

import com.kvs.admission.entity.MeritList;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MeritListRepository extends JpaRepository<MeritList, Long> {

    @EntityGraph(attributePaths = "entries")
    Optional<MeritList> findByMeritListId(UUID meritListId);
}
