package com.uaiou.users.repository;

import com.uaiou.users.entity.Admin;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminRepository extends JpaRepository<Admin, UUID> {}
