package com.miqu3iasg.banking.account.domain;

public enum AccountAction {
	BLOCK_ACCOUNT_USAGE {
		@Override
		public void apply (Account account) {
			account.block();
		}
	},
	UNBLOCK_ACCOUNT_USAGE {
		@Override
		public void apply (Account account) {
			account.unblock();
		}
	},
	CLOSE_ACCOUNT {
		@Override
		public void apply (Account account) {
			account.close();
		}
	};

	public abstract void apply (Account account);
}
