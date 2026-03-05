package com.miqu3iasg.banking.boleto.domain;

import java.util.Set;

public enum BoletoStatus {

	PENDING {
		@Override
		public Set<BoletoStatus> allowedTransitions () {
			return Set.of(PAID, EXPIRED);
		}
	},
	PAID {
		@Override
		public Set<BoletoStatus> allowedTransitions () {
			return Set.of();
		}
	},
	EXPIRED {
		@Override
		public Set<BoletoStatus> allowedTransitions () {
			return Set.of();
		}
	};

	public abstract Set<BoletoStatus> allowedTransitions ();

	public boolean canTransitionTo (BoletoStatus target) {
		return allowedTransitions().contains(target);
	}
}
