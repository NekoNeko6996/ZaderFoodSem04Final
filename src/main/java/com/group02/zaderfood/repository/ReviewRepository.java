package com.group02.zaderfood.repository;

import com.group02.zaderfood.entity.Review;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Integer> {

    // Thống kê Top 5 món ăn được đánh giá cao nhất
    // JPQL này giờ sẽ chạy được vì Review đã có property "recipe"
    @Query("SELECT r.name, AVG(rv.rating) as avgRating, COUNT(rv) as totalReviews "
            + "FROM Review rv "
            + "JOIN rv.recipe r "
            + "WHERE (r.isDeleted IS NULL OR r.isDeleted = false) "
            + // Chỉ lấy món chưa xóa
            "GROUP BY r.name "
            + "ORDER BY avgRating DESC, totalReviews DESC "
            + "LIMIT 5")
    List<Object[]> findTopRatedRecipes();
}
