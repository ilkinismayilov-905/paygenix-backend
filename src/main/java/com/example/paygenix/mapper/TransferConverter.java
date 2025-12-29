//package com.example.paygenix.converter;
//
//import com.example.paygenix.dto.request.TransferRequest;
//import com.example.paygenix.dto.response.TransferResponse;
//import com.example.paygenix.entity.Transfer;
//import org.springframework.stereotype.Component;
//
//import java.time.Instant;
//import java.time.LocalDate;
//import java.util.List;
//import java.util.stream.Collectors;
//
//@Component
//public class TransferConverter {
//
//    public TransferResponse toTransferResponse(Transfer transfer) {
//        return new TransferResponse(
//                mask(transfer.getFromCardNumber()),
//                mask(transfer.getToCardNumber()),
//                transfer.getAmount()
//        );
//    }
//
//    public List<TransferResponse> toTransferResponseList(List<Transfer> transfers) {
//        return transfers.stream()
//                .map(this::toTransferResponse)
//                .collect(Collectors.toList());
//    }
//
//    public Transfer toTransfer(TransferRequest request) {
//       return Transfer.builder()
//               .toCardNumber(request.toCardNumber())
//               .fromCardNumber(request.fromCardNumber())
//               .amount(request.amount())
//               .creationDate(Instant.now())
//               .build();
//    }
//
//
//    private static String mask(String cardNumber) {
//        return "**** **** **** " + cardNumber.substring(cardNumber.length() - 4);
//    }
//}
