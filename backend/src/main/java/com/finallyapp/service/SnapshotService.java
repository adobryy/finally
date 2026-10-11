package com.finallyapp.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Records the portfolio value every 30 seconds. */
@Service
public class SnapshotService {

	private final PortfolioService portfolioService;

	public SnapshotService(PortfolioService portfolioService) {
		this.portfolioService = portfolioService;
	}

	@Scheduled(fixedRate = 30_000)
	public void record() {
		portfolioService.recordSnapshot();
	}
}
