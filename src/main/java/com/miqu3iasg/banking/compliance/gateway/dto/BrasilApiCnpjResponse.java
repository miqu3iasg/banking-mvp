package com.miqu3iasg.banking.compliance.gateway.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response model representing a CNPJ (Cadastro Nacional da Pessoa Jurídica) record
 * as returned by the BrasilAPI public API.
 *
 * <p>This record maps the full JSON response from the endpoint:
 * {@code GET https://brasilapi.com.br/api/cnpj/v1/{cnpj}}
 *
 * <p>Fields are tolerant of additional or undocumented properties returned by the API
 * via {@code @JsonIgnoreProperties(ignoreUnknown = true)}.
 *
 * @see <a href="https://brasilapi.com.br/docs#tag/CNPJ">BrasilAPI - CNPJ Documentation</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BrasilApiCnpjResponse(

	/* The 14-digit CNPJ number (unformatted). */
	@JsonProperty("cnpj")
	String cnpj,

	/*
	 * Numeric identifier indicating whether this is a headquarters (matriz) or branch (filial).
	 *   1 - Headquarters (Matriz)
	 *   2 - Branch (Filial)
	 */
	@JsonProperty("identificador_matriz_filial")
	Integer identificadorMatrizFilial,

	/* Human-readable description of identificadorMatrizFilial. */
	@JsonProperty("descricao_identificador_matriz_filial")
	String descricaoIdentificadorMatrizFilial,

	/* Official corporate name (razão social) registered with the Receita Federal. */
	@JsonProperty("razao_social")
	String razaoSocial,

	/* Trade name (nome fantasia), if any, under which the company operates. */
	@JsonProperty("nome_fantasia")
	String nomeFantasia,

	/*
	 * Numeric code representing the current registration status (situação cadastral).
	 *   2 - Active (Ativa)
	 *   3 - Suspended (Suspensa)
	 *   4 - Unfit (Inapta)
	 *   8 - Closed (Baixada)
	 */
	@JsonProperty("situacao_cadastral")
	Integer situacaoCadastral,

	/* Human-readable description of the current registration status. */
	@JsonProperty("descricao_situacao_cadastral")
	String descricaoSituacaoCadastral,

	/* Date on which the current registration status was set (format: YYYY-MM-DD). */
	@JsonProperty("data_situacao_cadastral")
	String dataSituacaoCadastral,

	/* Numeric code for the reason behind the current registration status. */
	@JsonProperty("motivo_situacao_cadastral")
	Integer motivoSituacaoCadastral,

	/* Human-readable description of the reason for the current registration status. */
	@JsonProperty("descricao_motivo_situacao_cadastral")
	String descricaoMotivoSituacaoCadastral,

	/* Special situation label, if applicable (e.g., in liquidation or bankruptcy). */
	@JsonProperty("situacao_especial")
	String situacaoEspecial,

	/* Date on which the special situation was recorded (format: YYYY-MM-DD). */
	@JsonProperty("data_situacao_especial")
	String dataSituacaoEspecial,

	/* Numeric code for the legal nature (natureza jurídica) of the entity. */
	@JsonProperty("codigo_natureza_juridica")
	Integer codigoNaturezaJuridica,

	/* Human-readable description of the legal nature (e.g., Sociedade Limitada). */
	@JsonProperty("natureza_juridica")
	String naturezaJuridica,

	/* Date on which the company began its activities (format: YYYY-MM-DD). */
	@JsonProperty("data_inicio_atividade")
	String dataInicioAtividade,

	/* Primary CNAE (Classificação Nacional de Atividades Econômicas) activity code. */
	@JsonProperty("cnae_fiscal")
	Long cnaeFiscal,

	/* Human-readable description of the primary CNAE activity. */
	@JsonProperty("cnae_fiscal_descricao")
	String cnaeFiscalDescricao,

	/* List of secondary CNAE activities registered for this entity. */
	@JsonProperty("cnaes_secundarios")
	List<CnaeSecundario> cnaesSecundarios,

	/* Street name of the registered address. */
	@JsonProperty("logradouro")
	String logradouro,

	/* Type of public road (e.g., Rua, Avenida, Alameda). */
	@JsonProperty("descricao_tipo_de_logradouro")
	String descricaoTipoDeLogradouro,

	/* Street number of the registered address. */
	@JsonProperty("numero")
	String numero,

	/* Address complement (e.g., floor, suite, apartment). */
	@JsonProperty("complemento")
	String complemento,

	/* Neighborhood (bairro) of the registered address. */
	@JsonProperty("bairro")
	String bairro,

	/* Postal code (CEP) of the registered address (8 digits, unformatted). */
	@JsonProperty("cep")
	String cep,

	/* Brazilian state abbreviation (UF) of the registered address (e.g., SP, RJ). */
	@JsonProperty("uf")
	String uf,

	/* Municipality name of the registered address. */
	@JsonProperty("municipio")
	String municipio,

	/*
	 * IBGE code for the municipality as used by the Receita Federal.
	 * See also: codigoMunicipioIbge.
	 */
	@JsonProperty("codigo_municipio")
	Integer codigoMunicipio,

	/*
	 * Official IBGE code for the municipality.
	 * May differ from codigoMunicipio in some API responses.
	 */
	@JsonProperty("codigo_municipio_ibge")
	Integer codigoMunicipioIbge,

	/* City name if the entity is registered abroad. null for domestic entities. */
	@JsonProperty("nome_cidade_no_exterior")
	String nomeCidadeNoExterior,

	/* Country name if the entity is registered abroad. null for domestic entities. */
	@JsonProperty("pais")
	String pais,

	/* Country code if the entity is registered abroad. null for domestic entities. */
	@JsonProperty("codigo_pais")
	String codigoPais,

	/* Primary phone number including DDD area code (e.g., "11 99999-9999"). */
	@JsonProperty("ddd_telefone_1")
	String dddTelefone1,

	/* Secondary phone number including DDD area code, if available. */
	@JsonProperty("ddd_telefone_2")
	String dddTelefone2,

	/* Fax number including DDD area code, if available. */
	@JsonProperty("ddd_fax")
	String dddFax,

	/* Contact email address registered with the Receita Federal. */
	@JsonProperty("email")
	String email,

	/* Declared share capital (capital social) in Brazilian Reais (BRL). */
	@JsonProperty("capital_social")
	Double capitalSocial,

	/*
	 * Company size code as defined by the Receita Federal.
	 * See descricaoPorte for the human-readable label.
	 */
	@JsonProperty("porte")
	String porte,

	/* Numeric code for the company size (porte). */
	@JsonProperty("codigo_porte")
	Integer codigoPorte,

	/* Human-readable description of the company size (e.g., ME, EPP, Demais). */
	@JsonProperty("descricao_porte")
	String descricaoPorte,

	/* Whether the company has opted into the Simples Nacional tax regime. */
	@JsonProperty("opcao_pelo_simples")
	Boolean opcaoPeloSimples,

	/* Date of the Simples Nacional opt-in (format: YYYY-MM-DD). null if never opted in. */
	@JsonProperty("data_opcao_pelo_simples")
	String dataOpcaoPeloSimples,

	/* Date of exclusion from the Simples Nacional regime (format: YYYY-MM-DD). null if still active. */
	@JsonProperty("data_exclusao_do_simples")
	String dataExclusaoDoSimples,

	/* Whether the company has opted into the MEI (Microempreendedor Individual) regime. */
	@JsonProperty("opcao_pelo_mei")
	Boolean opcaoPeloMei,

	/* Date of the MEI opt-in (format: YYYY-MM-DD). null if never opted in. */
	@JsonProperty("data_opcao_pelo_mei")
	String dataOpcaoPeloMei,

	/* Date of exclusion from the MEI regime (format: YYYY-MM-DD). null if still active. */
	@JsonProperty("data_exclusao_do_mei")
	String dataExclusaoDoMei,

	/* Qualification code of the responsible party (responsável) for this CNPJ. */
	@JsonProperty("qualificacao_do_responsavel")
	Integer qualificacaoDoResponsavel,

	/*
	 * Name of the federative entity (ente federativo) responsible for this CNPJ.
	 * Typically populated for public-sector entities; null otherwise.
	 */
	@JsonProperty("ente_federativo_responsavel")
	String enteFederativoResponsavel,

	/*
	 * Corporate structure (Quadro de Sócios e Administradores — QSA):
	 * list of partners, shareholders, and administrators.
	 */
	@JsonProperty("qsa")
	List<Socio> qsa,

	/*
	 * Tax regime (regime tributário) records associated with this CNPJ,
	 * grouped by fiscal year.
	 */
	@JsonProperty("regime_tributario")
	List<RegimeTributario> regimeTributario

) {

	/**
	 * Represents a secondary CNAE (Classificação Nacional de Atividades Econômicas) entry
	 * associated with the company.
	 *
	 * @see <a href="https://brasilapi.com.br/docs#tag/CNPJ">BrasilAPI - CNPJ Documentation</a>
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record CnaeSecundario(

		/* Numeric CNAE activity code. */
		@JsonProperty("codigo") Long codigo,

		/* Human-readable description of the CNAE activity. */
		@JsonProperty("descricao") String descricao

	) { }

	/**
	 * Represents a partner, shareholder, or administrator (Sócio/Administrador)
	 * as listed in the QSA (Quadro de Sócios e Administradores).
	 *
	 * @see <a href="https://brasilapi.com.br/docs#tag/CNPJ">BrasilAPI - CNPJ Documentation</a>
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Socio(

		/*
		 * Identifier for the type of partner.
		 *   1 - Legal entity (Pessoa Jurídica)
		 *   2 - Individual (Pessoa Física)
		 *   3 - Foreign individual (Estrangeiro)
		 */
		@JsonProperty("identificador_de_socio") Integer identificadorDeSocio,

		/* Full name of the partner or administrator. */
		@JsonProperty("nome_socio") String nomeSocio,

		/* CPF (individuals) or CNPJ (legal entities) of the partner, partially masked. */
		@JsonProperty("cnpj_cpf_do_socio") String cnpjCpfDoSocio,

		/* Numeric code for the partner's qualification role. */
		@JsonProperty("codigo_qualificacao_socio") Integer codigoQualificacaoSocio,

		/* Human-readable description of the partner's qualification (e.g., Sócio-Administrador). */
		@JsonProperty("qualificacao_socio") String qualificacaoSocio,

		/* Date on which this partner joined the company (format: YYYY-MM-DD). */
		@JsonProperty("data_entrada_sociedade") String dataEntradaSociedade,

		/* Country of residence of the partner. null for domestic partners. */
		@JsonProperty("pais") String pais,

		/* Country code of the partner's country of residence. null for domestic partners. */
		@JsonProperty("codigo_pais") String codigoPais,

		/* CPF of the legal representative. null if not applicable. */
		@JsonProperty("cpf_representante_legal") String cpfRepresentanteLegal,

		/* Full name of the legal representative. null if not applicable. */
		@JsonProperty("nome_representante_legal") String nomeRepresentanteLegal,

		/* Qualification code of the legal representative. null if not applicable. */
		@JsonProperty("codigo_qualificacao_representante_legal") Integer codigoQualificacaoRepresentanteLegal,

		/* Human-readable qualification description of the legal representative. null if not applicable. */
		@JsonProperty("qualificacao_representante_legal") String qualificacaoRepresentanteLegal,

		/* Age range label of the partner (e.g., "Entre 31 a 40 anos"). */
		@JsonProperty("faixa_etaria") String faixaEtaria,

		/* Numeric code for the partner's age range. */
		@JsonProperty("codigo_faixa_etaria") Integer codigoFaixaEtaria

	) { }

	/**
	 * Represents a tax regime (Regime Tributário) record associated with the CNPJ
	 * for a given fiscal year.
	 *
	 * @see <a href="https://brasilapi.com.br/docs#tag/CNPJ">BrasilAPI - CNPJ Documentation</a>
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record RegimeTributario(

		/* Fiscal year to which this tax regime record applies. */
		@JsonProperty("ano") Integer ano,

		/*
		 * CNPJ of the SCP (Sociedade em Conta de Participação) linked to this regime.
		 * null if not applicable.
		 */
		@JsonProperty("cnpj_da_scp") String cnpjDaScp,

		/* Tax regime description (e.g., Lucro Real, Lucro Presumido, Simples Nacional). */
		@JsonProperty("forma_de_tributacao") String formaDeTributacao,

		/* Number of bookkeeping records (escriturações) filed for this fiscal year. */
		@JsonProperty("quantidade_de_escrituracoes") Integer quantidadeDeEscritutacoes

	) { }
}
