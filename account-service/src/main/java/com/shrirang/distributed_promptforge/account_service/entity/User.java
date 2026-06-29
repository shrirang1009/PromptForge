package com.shrirang.distributed_promptforge.account_service.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "users")
public class User {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    String username;
    String password;
    String name;
    @Enumerated(EnumType.STRING)
    @Builder.Default
    UserRole role = UserRole.USER;
    @Builder.Default
    Boolean blocked = false;

    @Builder.Default
    Boolean emailVerified = false;


    String passwordResetCode;
    Instant passwordResetCodeExpiresAt;

    @Column(unique = true)
    String stripeCustomerId;

    // Razorpay
    String razorpayCustomerId;

    @CreationTimestamp
    Instant createdAt;

    @UpdateTimestamp
    Instant updatedAt;

    Instant deletedAt; //soft delete
}
