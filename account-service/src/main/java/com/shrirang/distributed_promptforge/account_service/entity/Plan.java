package com.shrirang.distributed_promptforge.account_service.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
public class Plan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    String name;

    @Column(unique = true)
    String stripePriceId;

    // Razorpay fields
    String razorpayPlanId;
    Long priceInPaise; // Price in smallest currency unit (paise for INR)

    Integer maxProjects;
    Integer maxTokensPerDay;
    Integer maxPreviews; //max number of previews allowed per plan
    Boolean unlimitedAi; //unlimited access to LLM, ignore maxTokensPerDay if true
    Integer validityDays; // validity window after payment (e.g. 30 days)

    Boolean active;
}
