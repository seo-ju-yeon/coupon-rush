package dev.portfolio.couponrush.domain.order.repository;

import dev.portfolio.couponrush.domain.order.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {
}
