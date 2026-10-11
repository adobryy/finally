package com.finallyapp.controller;

import java.util.List;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.finallyapp.controller.dto.TradeRequest;
import com.finallyapp.model.Portfolio;
import com.finallyapp.model.PortfolioSnapshot;
import com.finallyapp.model.TradeResult;
import com.finallyapp.service.PortfolioService;

import reactor.core.publisher.Mono;

/** Portfolio view and market-order trading. */
@RestController
@RequestMapping("/api/portfolio")
public class PortfolioController {

	private final PortfolioService portfolioService;

	public PortfolioController(PortfolioService portfolioService) {
		this.portfolioService = portfolioService;
	}

	@GetMapping
	public Mono<Portfolio> portfolio() {
		return portfolioService.portfolio();
	}

	@PostMapping("/trade")
	public Mono<TradeResult> trade(@Validated @RequestBody TradeRequest request) {
		return portfolioService.trade(request.ticker(), request.side(), request.quantity());
	}

	@GetMapping("/history")
	public Mono<List<PortfolioSnapshot>> history() {
		return portfolioService.history();
	}
}
