package com.jojoldu.book.springboot.domain.posts;

import org.springframework.data.jpa.repository.JpaRepository;
import com.jojoldu.book.springboot.domain.posts.Posts;

public interface PostsRepository extends JpaRepository<Posts,Long> {
}
