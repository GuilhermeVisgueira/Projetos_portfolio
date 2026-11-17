package com.visgueira.ecommerce.service;

import com.visgueira.ecommerce.dto.ShippingQuoteResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class ShippingService {

    // Frete PAC grátis para pedidos com valor líquido >= R$ 500,00 (50000 centavos)
    public static final long FREE_SHIPPING_THRESHOLD_CENTS = 50000L;

    /**
     * Calcula cotação de frete (PAC ou SEDEX) baseada na região do CEP, peso total em gramas
     * e valor líquido do carrinho (para elegibilidade de frete grátis no PAC).
     */
    public ShippingQuoteResponse calculateShipping(String rawZipCode, int totalWeightGrams, long netSubtotalCents, String serviceType) {
        String cleanCep = validateAndNormalizeZipCode(rawZipCode);
        if (totalWeightGrams <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O peso total para cálculo de frete deve ser maior que zero.");
        }
        if (netSubtotalCents < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O subtotal para cálculo de frete não pode ser negativo.");
        }

        String normalizedService = (serviceType == null || serviceType.isBlank())
            ? "PAC"
            : serviceType.trim().toUpperCase();

        if (!"PAC".equals(normalizedService) && !"SEDEX".equals(normalizedService)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Modalidade de frete inválida. Use PAC ou SEDEX.");
        }

        char regionDigit = cleanCep.charAt(0);
        String regionName;
        long basePacCents;
        int basePacDays;

        switch (regionDigit) {
            case '0', '1', '2', '3' -> {
                regionName = "Sudeste";
                basePacCents = 1890L; // R$ 18,90
                basePacDays = 5;
            }
            case '8', '9' -> {
                regionName = "Sul";
                basePacCents = 2490L; // R$ 24,90
                basePacDays = 6;
            }
            case '7' -> {
                regionName = "Centro-Oeste";
                basePacCents = 2990L; // R$ 29,90
                basePacDays = 7;
            }
            case '4', '5' -> {
                regionName = "Nordeste";
                basePacCents = 3490L; // R$ 34,90
                basePacDays = 9;
            }
            default -> {
                regionName = "Norte";
                basePacCents = 4290L; // R$ 42,90
                basePacDays = 12;
            }
        }

        // Adicional por quilo extra acima de 1kg (R$ 5,00 por kg adicional iniciado)
        int extraKg = Math.max(0, (totalWeightGrams - 1) / 1000);
        long weightSurchargeCents = extraKg * 500L;

        if ("PAC".equals(normalizedService)) {
            boolean isFree = netSubtotalCents >= FREE_SHIPPING_THRESHOLD_CENTS;
            long finalPacCents = isFree ? 0L : (basePacCents + weightSurchargeCents);
            return new ShippingQuoteResponse(cleanCep, regionName, "PAC", finalPacCents, basePacDays, isFree);
        } else {
            // SEDEX: 1.8x a base do PAC + sobretaxa de peso, com prazo reduzido
            long sedexCents = Math.round(basePacCents * 1.8) + weightSurchargeCents;
            int sedexDays = Math.max(1, basePacDays / 2);
            return new ShippingQuoteResponse(cleanCep, regionName, "SEDEX", sedexCents, sedexDays, false);
        }
    }

    public List<ShippingQuoteResponse> quoteAllOptions(String rawZipCode, int totalWeightGrams, long netSubtotalCents) {
        return List.of(
            calculateShipping(rawZipCode, totalWeightGrams, netSubtotalCents, "PAC"),
            calculateShipping(rawZipCode, totalWeightGrams, netSubtotalCents, "SEDEX")
        );
    }

    public String validateAndNormalizeZipCode(String rawZipCode) {
        if (rawZipCode == null || rawZipCode.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O CEP é obrigatório para o cálculo de frete.");
        }
        String digits = rawZipCode.replaceAll("\\D", "");
        if (digits.length() != 8 || digits.equals("00000000")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CEP inválido. Informe um CEP com 8 dígitos numéricos.");
        }
        return digits;
    }
}
