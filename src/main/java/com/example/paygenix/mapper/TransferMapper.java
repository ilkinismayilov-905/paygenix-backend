package com.example.paygenix.mapper;

import com.example.paygenix.dto.response.TransferResponse;
import com.example.paygenix.entity.Transfer;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
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

    default String mask(String cardNumber) {
        return "**** **** **** " + cardNumber.substring(cardNumber.length() - 4);
    }
}
