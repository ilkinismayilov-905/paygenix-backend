package com.example.paygenix.mapper;

import com.example.paygenix.dto.request.PaymentRequest;
import com.example.paygenix.dto.request.TransferRequest;
import com.example.paygenix.dto.response.PaymentResponse;
import com.example.paygenix.entity.Payment;
import com.example.paygenix.entity.Transfer;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.Instant;
import java.util.List;

@Mapper(componentModel = "spring",imports = Instant.class)
public interface PaymentMapper {


    @Mapping(
            target = "fromCardNumber",
            expression = "java(mask(payment.getFromCardNumber()))"
    )
    PaymentResponse toPaymentResponse(Payment payment);

    List<PaymentResponse> toPaymentResponseList(List<Payment> payments);

    @Mapping(
            target = "creationDate",expression = "java(Instant.now())"
    )
    Payment mapToPayment(PaymentRequest paymentRequest);

    default String mask(String cardNumber) {
        return "**** **** **** " + cardNumber.substring(cardNumber.length() - 4);
    }

}
