package com.example.paygenix.mapper;

import com.example.paygenix.dto.request.TransferRequest;
import com.example.paygenix.dto.response.TransferResponse;
import com.example.paygenix.entity.Transfer;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.Instant;
import java.util.List;

@Mapper(componentModel = "spring", imports = Instant.class)
public interface TransferMapper {

    @Mapping(
            target = "fromCardNumber",
            expression = "java(mask(transfer.getFromCardNumber()))"
    )
    @Mapping(
            target = "toCardNumber",
            expression = "java(mask(transfer.getToCardNumber()))"
    )
    TransferResponse mapToTransferResponse(Transfer transfer);

    List<TransferResponse> mapToTransferResponseList(List<Transfer> transferList);

    @Mapping(
            target = "creationDate",expression = "java(Instant.now())"
    )
    Transfer mapToTransfer(TransferRequest transferRequest);

    default String mask(String cardNumber) {
        return "**** **** **** " + cardNumber.substring(cardNumber.length() - 4);
    }
}
