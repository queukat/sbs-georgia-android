# Import Fixtures Index

Fixtures live in `app/src/test/resources/fixtures`. They are extracted text samples,
not original PDFs. Use them to harden parsers without requiring Android file access
or private taxpayer documents.

## TBC statement fixtures

| Fixture | Covers | Primary tests |
| --- | --- | --- |
| `tbc_statement_v1_extracted.txt` | Baseline current TBC v1 extracted-text layout. | `TbcStatementParserTest`, `StatementImportUseCasesTest` |
| `tbc_statement_v1_multipage_extracted.txt` | Multi-page extraction with continued transaction rows. | `TbcStatementParserTest` |
| `tbc_statement_v1_wrapped_extracted.txt` | Wrapped/multi-line transaction row assembly. | `TbcStatementParserTest`, `StatementImportUseCasesTest` |
| `tbc_statement_v1_whitespace_extracted.txt` | Layout with spacing variance between columns. | `TbcStatementParserTest` |
| `tbc_statement_v1_overlap_extracted.txt` | Duplicate/overlap handling in preview quality selection. | `StatementImportUseCasesTest` |
| `tbc_statement_v1_georgian_extracted.txt` | Georgian labels/text in TBC statement extraction. | `TbcStatementParserTest` |
| `tbc_statement_v1_bilingual_headers_extracted.txt` | Bilingual headers and mixed-language hints. | `TbcStatementParserTest` |
| `tbc_statement_v1_collapsed_bilingual_extracted.txt` | Realistic collapsed bilingual extraction from PdfBox. | `TbcStatementParserTest` |
| `tbc_statement_business_gel_extracted.txt` | Anonymized business GEL statement: net card settlements, commission text, bank fees and fee-funding credits. | `TbcBusinessStatementParserTest` |

Related production code:

- `domain/service/tbc/TbcStatementParser.kt`
- `domain/service/tbc/TbcLogicalRowAssembler.kt`
- `domain/service/tbc/TbcTransactionLineParser.kt`
- `domain/service/tbc/TbcStatementHeuristics.kt`
- `domain/service/TaxPaymentDetection.kt`
- `domain/usecase/StatementImportUseCases.kt`

## Onboarding document fixtures

| Fixture | Covers | Primary tests |
| --- | --- | --- |
| `registry_extract_en_extracted.txt` | Synthetic English registry extract shape. | `OnboardingDocumentParsersTest` |
| `registry_extract_en_real_pdftext.txt` | Real PdfBox text shape for English registry extract. | `OnboardingDocumentParsersTest` |
| `registry_extract_ka_extracted.txt` | Georgian registry labels and field mapping. | `OnboardingDocumentParsersTest` |
| `sbs_certificate_ka_extracted.txt` | Synthetic Georgian SBS certificate shape. | `OnboardingDocumentParsersTest` |
| `sbs_certificate_ka_real_pdftext.txt` | Real PdfBox text shape for Georgian SBS certificate. | `OnboardingDocumentParsersTest` |

Related production code:

- `domain/service/onboarding/OnboardingDocumentParser.kt`
- `domain/service/onboarding/RegistryExtractParser.kt`
- `domain/service/onboarding/SmallBusinessStatusCertificateParser.kt`
- `domain/service/onboarding/FieldExtractors.kt`
- `domain/service/onboarding/DocumentTextNormalizer.kt`

## Known gaps

- Original PDFs are not checked in; fixtures are text snapshots only.
- Scanned/OCR-only documents are out of scope.
- TBC v1 is the only supported bank statement family.
- No fixtures currently cover other Georgian banks or bank API exports.
- Coverage is pragmatic for English/Georgian/bilingual TBC extraction, but materially
  different future TBC exports should be captured before expanding heuristics.
- Private production-layout audits are represented only by synthetic tests for
  extra description columns, missing money columns, balance mismatches, repeated
  currency headers and FX-conversion credits; no source PDF/text is retained.
- Onboarding coverage is conservative until more real registry/certificate samples
  are available.

## Adding a new fixture

1. Strip or anonymize taxpayer IDs, names, addresses and account numbers.
2. Save extracted text under `app/src/test/resources/fixtures`.
3. Name the file by source and shape, for example
   `tbc_statement_v1_new_layout_extracted.txt`.
4. Add parser-level tests first, then use-case tests if preview/duplicate/FX behavior
   changes.
5. Prefer asserting domain facts over brittle full-text snapshots.
6. Update this index with the coverage purpose and related tests.

## When import behavior changes

- Parser-only changes should usually touch `TbcStatementParserTest`.
- Preview scoring, duplicate handling, confirm import or tax-payment detection should
  usually touch `StatementImportUseCasesTest`.
- Android picker/PdfBox integration changes may need connected tests or manual QA.
- Keep `Uri` and Android file IO outside domain use-case APIs.
