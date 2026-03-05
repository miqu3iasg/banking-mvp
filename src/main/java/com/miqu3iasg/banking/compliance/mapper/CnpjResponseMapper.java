package com.miqu3iasg.banking.compliance.mapper;

import com.miqu3iasg.banking.compliance.api.dto.CnpjResponse;
import com.miqu3iasg.banking.compliance.gateway.dto.BrasilApiCnpjResponse;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class CnpjResponseMapper {
	public CnpjResponse toResponse (BrasilApiCnpjResponse raw) {
		return new CnpjResponse(
			raw.cnpj(),
			raw.descricaoIdentificadorMatrizFilial(),
			raw.razaoSocial(),
			raw.nomeFantasia(),
			raw.descricaoSituacaoCadastral(),
			raw.dataSituacaoCadastral(),
			raw.descricaoMotivoSituacaoCadastral(),
			raw.codigoNaturezaJuridica(),
			raw.naturezaJuridica(),
			raw.dataInicioAtividade(),
			raw.cnaeFiscal(),
			raw.cnaeFiscalDescricao(),
			mapSecondaryActivities(raw.cnaesSecundarios()),
			mapAddress(raw),
			raw.dddTelefone1(),
			raw.dddTelefone2(),
			raw.dddFax(),
			raw.capitalSocial(),
			raw.porte(),
			raw.opcaoPeloSimples(),
			raw.opcaoPeloMei(),
			raw.email(),
			mapPartners(raw.qsa())
		);
	}

	private List<CnpjResponse.SecondaryActivity> mapSecondaryActivities (
		List<BrasilApiCnpjResponse.CnaeSecundario> cnaes
	) {
		if (cnaes == null) return Collections.emptyList();
		return cnaes.stream()
			.map(c -> new CnpjResponse.SecondaryActivity(c.codigo(), c.descricao()))
			.toList();
	}

	private CnpjResponse.Address mapAddress (BrasilApiCnpjResponse raw) {
		return new CnpjResponse.Address(
			raw.logradouro(),
			raw.numero(),
			raw.complemento(),
			raw.bairro(),
			raw.cep(),
			raw.uf(),
			raw.municipio(),
			raw.codigoMunicipioIbge()
		);
	}

	private List<CnpjResponse.Partner> mapPartners (List<BrasilApiCnpjResponse.Socio> socios) {
		if (socios == null) return Collections.emptyList();
		return socios.stream()
			.map(s -> new CnpjResponse.Partner(
				s.identificadorDeSocio(),
				s.nomeSocio(),
				s.cnpjCpfDoSocio(),
				s.codigoQualificacaoSocio(),
				s.qualificacaoSocio(),
				s.dataEntradaSociedade(),
				s.faixaEtaria(),
				s.nomeRepresentanteLegal(),
				s.cpfRepresentanteLegal()
			))
			.toList();
	}
}
