package dev.portfolio.couponrush.domain.post.repository;

import dev.portfolio.couponrush.domain.post.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostRepository extends JpaRepository<Post, Long> {
}
