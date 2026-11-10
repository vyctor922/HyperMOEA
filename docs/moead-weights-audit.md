# Auditoria Completa dos Vetores de Pesos do MOEA/DD (T11)

**Projeto:** `hypeMOEA` (Java 21, Maven, jMetal 7.8)  
**Tarefa:** T11 — Auditoria dos arquivos em `MOEAD_Weights/`, comparação com os recursos do jMetal, cópia fiel e substituição opcional de zeros  
**Agente responsável:** Agente B (T11)

---

## 1. Resumo Executivo

Esta auditoria cumpriu integralmente as quatro partes especificadas em `tasks/task11.md` e `TASK_COORDINATOR.md`:

1. **Inspeção dos JARs do jMetal 7.8 e do repositório oficial do jMetal:**
   - Os 5 JARs resolvidos pelo Maven em `~/.m2/repository/org/uma/jmetal/*/7.8/*.jar` (`jmetal-core-7.8.jar`, `jmetal-algorithm-7.8.jar`, `jmetal-component-7.8.jar`, `jmetal-lab-7.8.jar`, `jmetal-problem-7.8.jar`) foram inspecionados via `ZipFile` e **não empacotam** o diretório `resources/weightVectorFiles/`. No jMetal 7.8, esses recursos residem na árvore do repositório Git oficial (`jMetal/resources/weightVectorFiles/moead/`), que contém **29 arquivos rastreados pelo Git**.
2. **Inventário completo de `MOEAD_Weights/` (43 arquivos originais no commit `6a6ecc2` e 48 arquivos atuais após o commit `f5b1eef`):**
   - No commit inicial `6a6ecc2`, `MOEAD_Weights/` continha **43 arquivos** (originários do diretório histórico `NewMOEA/MOEAD_Weights`): 15 arquivos idênticos ao jMetal, 9 arquivos que possuíam contraparte no jMetal mas divergiam por formato ou pela substituição `0 -> 1e-5`, e 19 arquivos sem contraparte no repositório oficial do jMetal.
   - No commit `f5b1eef` (`03/11/2026`), os 29 arquivos oficiais de `jMetal/resources/weightVectorFiles/moead/` foram sincronizados para `MOEAD_Weights/` (sobrescrevendo os 9 arquivos que divergiam e adicionando 5 arquivos oficiais do jMetal antes ausentes: `W3D_136.dat`, `W5D_495.dat`, `W10D_715.dat`, `W15D_120.dat` e `W15D_680.dat`), totalizando **48 arquivos** em `MOEAD_Weights/`.
3. **Anomalia matemática identificada nos arquivos oficiais do jMetal `W8D_156.dat` e `W10D_275.dat`:**
   - Nos arquivos oficiais do jMetal 7.8 para $m=8$ (`W8D_156.dat`, 156 linhas) e $m=10$ (`W10D_275.dat`, 275 linhas), a camada externa ($H_1=3$, com 120 e 220 vetores) e a camada interna ($H_2=2$, com 36 e 55 vetores) foram concatenadas pelos autores do jMetal **sem aplicar a contração centrípeta** $w_i^{\text{inner}} = \frac{1}{2}x_i + \frac{1}{2m}$ de Deb & Jain (2014).
   - Como consequência, os $m$ vértices unitários extremos $(1,0,\dots,0), \dots, (0,\dots,0,1)$ aparecem duas vezes (uma na camada externa e outra na camada interna), gerando **8 pares duplicados exatos em `W8D_156.dat`** e **10 pares duplicados exatos em `W10D_275.dat`**.
4. **Auditoria de bytecode sobre divisão por zero ($w_i = 0$):**
   - Provou-se por desmontagem de bytecode (`javap -c -p`) que **`MOEA/DD`**, **`MOEA/D`** e **`MOEA/D-DRA`** (`AbstractMOEAD.fitnessFunction` com `PBI`, `TCHE` ou `AGG`, bem como `MOEADD.calculateDistance2`) e o indicador **`R2`** (`ASFUtilityFunctionSet`) **não dividem por zero** quando $w_i = 0$.
   - Por outro lado, **`WASFGA`** e **`GWASFGA`** invocam `WeightVectors.invert(weights, true)`, que executa `1.0 / w[i][j]` (`ddiv` nos offsets 47, 81 e 119 de `WeightVectors.class`), produzindo `Infinity` e `NaN` (`Infinity / Infinity`) sempre que $w_{i,j} = 0.0$. Essa é a razão técnica histórica pela qual os arquivos em `NewMOEA/MOEAD_Weights` substituíam `0` por `1e-5`.
5. **Cópia fiel, manifesto e procedimento opcional `-DreplaceZeroWeights=false`:**
   - Todos os 29 arquivos oficiais do jMetal foram copiados fielmente (100% idênticos byte a byte e por hash SHA-256) para `resources/weightVectorFiles/` e `resources/weightVectorFiles/`.
   - O manifesto `docs/moead-weights-manifest.csv` foi gerado com todos os hashes SHA-256 e classificações.
   - O suporte à propriedade JVM `-DreplaceZeroWeights=false` (desativada por padrão) foi implementado em `ReferenceSetValidator` com geração controlada em `resources/weightVectorFiles/weights_gen/`, sem tocar nos arquivos originais nem alterar `WFGStudy.java`.

---

## 2. Inspeção dos JARs do jMetal 7.8 vs. Repositório Oficial

A verificação direta via `System.IO.Compression.ZipFile` nos 5 artefatos Maven do jMetal 7.8 em `C:\Users\vyctor922\.m2\repository\org\uma\jmetal\`:

- `jmetal-core-7.8.jar`: contém apenas `Weight_2d_1000.ws` e `Weight_3d_1128.ws` (usados pelo indicador `R2` em `resources/weightVectorFiles/`), mas **nenhum** arquivo `W*D_*.dat`.
- `jmetal-algorithm-7.8.jar`, `jmetal-component-7.8.jar`, `jmetal-lab-7.8.jar`, `jmetal-problem-7.8.jar`: **zero** arquivos `.dat`.

No repositório Git oficial do jMetal (`C:\Users\vyctor922\Downloads\Mestrado\tema\experimento\java\jMetal`), o comando `git ls-files resources/weightVectorFiles/moead/` lista exatamente os **29 arquivos oficiais** distribuídos pelo projeto jMetal:

- **$m=2$ (6 arquivos):** `W2D_300.dat`, `W2D_400.dat`, `W2D_500.dat`, `W2D_600.dat`, `W2D_800.dat`, `W2D_1000.dat`
- **$m=3$ (9 arquivos):** `W3D_91.dat`, `W3D_100.dat`, `W3D_136.dat`, `W3D_300.dat`, `W3D_500.dat`, `W3D_600.dat`, `W3D_800.dat`, `W3D_1000.dat`, `W3D_1200.dat`
- **$m=4$ (1 arquivo):** `W4D_165.dat` (separado por vírgula `,`)
- **$m=5$ (7 arquivos):** `W5D_495.dat`, `W5D_1000.dat`, `W5D_1200.dat`, `W5D_1500.dat`, `W5D_1800.dat`, `W5D_2000.dat`, `W5D_2500.dat`
- **$m=8$ (1 arquivo):** `W8D_156.dat`
- **$m=10$ (3 arquivos):** `W10D_220.dat`, `W10D_275.dat`, `W10D_715.dat`
- **$m=15$ (2 arquivos):** `W15D_120.dat`, `W15D_680.dat`

Observa-se que o repositório oficial do jMetal **não possui** os arquivos ativos `W2D_100.dat` ($m=2, N=100$), `W5D_210.dat` ($m=5, N=210$) nem `W15D_135.dat` ($m=15, N=135$). Esses três arquivos ativos provêm exclusivamente do acervo `MOEAD_Weights/` do projeto (`NewMOEA`).

---

## 3. Inventário Completo de `MOEAD_Weights/`

### 3.1 Evolução entre o commit inicial (`6a6ecc2`, 43 arquivos) e o estado atual (`f5b1eef`, 48 arquivos)

No commit `6a6ecc2`, `MOEAD_Weights/` continha **43 arquivos**. No commit `f5b1eef`, a sincronização com `jMetal/resources/weightVectorFiles/moead/` realizou duas alterações:
1. **Adicionou 5 arquivos oficiais do jMetal** que não estavam entre os 43 originais: `W3D_136.dat`, `W5D_495.dat`, `W10D_715.dat`, `W15D_120.dat` e `W15D_680.dat`.
2. **Substituiu 9 arquivos** que possuíam contraparte homônima no jMetal mas divergiam no commit `6a6ecc2`:
   - 6 arquivos que tinham substituição `0 -> 1e-5` (TAB, 15 dígitos) no commit `6a6ecc2` foram substituídos pelos oficiais do jMetal (SPACE, com zeros `0.0`): `W2D_300.dat` (SHA-256 anterior `f2a45d8ddad9bd09773e7b5c47de54a29645812a6cc01446e7e7029acd8bcd62`), `W3D_91.dat` (SHA-256 anterior `0ac89a15009da816386da8380f5971132ab3da95bef6f76a0290c0382dbfc897`), `W8D_156.dat` (SHA-256 anterior `e6b23ae6879e0b4581c651436d5c9d44fd93f4e6a65487259531c725a05c00c7`), `W10D_220.dat` (SHA-256 anterior `e3530a5eb40184b9543b56135792e2143021476a656c5a27f2fa4bb6502c1aea`) e `W10D_275.dat` (SHA-256 anterior `ff188511a399004d68efcf783b79e30aa10a9ead2af0647e868d9cd54cc0b2a1`).
   - 2 arquivos (`W3D_100.dat`, SHA-256 anterior `1cd9ddda87225073999f94b07910ae768f3ebb569ae51719d6dd501a7cc62be2`, e `W3D_300.dat`, SHA-256 anterior `af1f9638fcc0690405883830fb212f41f38bee5f77da801da792f2cd71d8dc0f`) que usavam precisão de 6 casas decimais foram substituídos pelas versões do jMetal de 16 casas decimais.
   - 2 arquivos (`W2D_400.dat` e `W2D_500.dat`) tiveram apenas normalização de quebras de linha.

### 3.2 Tabela Completa dos 48 Arquivos em `MOEAD_Weights/`

| # | Arquivo | Bytes | SHA-256 | $m$ | Vetores | Separador (EOL) | Presença de Zeros | Soma Mín / Máx | Duplicatas | Classificação |
|---:|:---|---:|:---|---:|---:|:---|:---|:---|---:|:---|
| 1 | `W10D_220.dat` | 17340 | `1f3a1e5ab2a5adb66003ecacad40837d86841a04373cf71cecd46d90feffc689` | 10 | 220 | SPACE (CRLF) | Sim (1650) | 1.00000000 / 1.00000000 | 0 | Idêntico ao jMetal (Legado) |
| 2 | `W10D_275.dat` | 19375 | `91cca614fe043c8952f383da37134d108089e14b52815a68f3f0b7231d717583` | 10 | 275 | SPACE (CRLF) | Sim (2100) | 1.00000000 / 1.00000000 | 10 | Idêntico ao jMetal (Ativo) |
| 3 | `W10D_715.dat` | 31770 | `cef31aedc98874e8a8fae7063d172d2bfa3da4a3ace56754149997d970f52603` | 10 | 715 | SPACE (CRLF) | Sim (4950) | 1.00000000 / 1.00000000 | 0 | Idêntico ao jMetal (Legado) |
| 4 | `W15D_120.dat` | 7440 | `a110eb8c72412e1f9878d6f0db8c88fd92bf481127f0284eb17826917c4fa7a0` | 15 | 120 | SPACE (CRLF) | Sim (1575) | 1.00000000 / 1.00000000 | 0 | Idêntico ao jMetal (Legado) |
| 5 | `W15D_135.dat` | 41280 | `52d429f53ce81b4ab0c33468a6974e1a027c502a631aba9f8e49047f5d2fbcc1` | 15 | 135 | TAB (CRLF) | Não (1e-5: 1575) | 1.00007000 / 1.00014000 | 0 | Sem contraparte jMetal; 0->1e-5 (Ativo) |
| 6 | `W15D_680.dat` | 68935 | `bfc75ed8ce982eed5eff6eb864e7378d8a4dd37538d5f3a756242caf3dad31d3` | 15 | 680 | SPACE (CRLF) | Sim (8400) | 1.00000000 / 1.00000000 | 0 | Idêntico ao jMetal (Legado) |
| 7 | `W2D_100.dat` | 3692 | `8bdd2bd030389f3667bd15bb9d282095aa69d0c7e3eb5222aaafdac136caa8b9` | 2 | 100 | TAB (CRLF) | Não (1e-5: 2) | 1.00000000 / 1.00001000 | 0 | Sem contraparte jMetal; 0->1e-5 (Ativo) |
| 8 | `W2D_1000.dat` | 19957 | `38f9a76ba8d59eeb07ab3bcd2cdffa1cf5331f3b8ee0b0c9aac7df9ee726380c` | 2 | 1000 | SPACE (CRLF) | Sim (2) | 0.99999950 / 1.00000050 | 0 | Idêntico ao jMetal (Legado) |
| 9 | `W2D_1200.dat` | 44636 | `943c8f3aa858f02b2b74b502c205bcba535248ea89fc5b6f33f3b657b986b99f` | 2 | 1200 | TAB (CRLF) | Não (1e-5: 2) | 1.00000000 / 1.00001000 | 0 | Sem contraparte jMetal; 0->1e-5 (Legado) |
| 10 | `W2D_200.dat` | 7414 | `330fb3f15215bbc8075ebe51aa17b2eb67b7ad4f99aded4a6fbb9f215a22be9b` | 2 | 200 | TAB (CRLF) | Não (1e-5: 2) | 1.00000000 / 1.00001000 | 0 | Sem contraparte jMetal; 0->1e-5 (Legado) |
| 11 | `W2D_300.dat` | 5985 | `a6173b1293060be424f876b33b61a3f21e3b0aeca2685394ea286732ccd8e3f2` | 2 | 300 | SPACE (CRLF) | Sim (2) | 0.99999950 / 1.00000050 | 0 | Idêntico ao jMetal (Legado) |
| 12 | `W2D_400.dat` | 7982 | `f4c21b837a6c90d0e7c552727871be1812cb6220cd447fd06e4d1f4903c8d554` | 2 | 400 | SPACE (CRLF) | Sim (2) | 0.99999950 / 1.00000050 | 0 | Idêntico ao jMetal (Legado) |
| 13 | `W2D_500.dat` | 9978 | `83c67623e602850d01426ac17031148d36f95d5567722ed378d37b7a87cf4dd2` | 2 | 500 | SPACE (CRLF) | Sim (2) | 0.99999950 / 1.00000050 | 0 | Idêntico ao jMetal (Legado) |
| 14 | `W2D_6.dat` | 192 | `1b73c3fb81a9d010cce3812305f80bd3fd53a498c01b31422a5392251fec4a27` | 2 | 7 | TAB (CRLF) | Sim (2) | 1.00000000 / 1.00000000 | 0 | Sem contraparte jMetal; zeros preservados (Legado) |
| 15 | `W2D_600.dat` | 11985 | `512725dcc4703a8fcaee7829c28ec8797af7db142ad465e15998dab74d91f1b7` | 2 | 600 | SPACE (CRLF) | Sim (2) | 0.99999950 / 1.00000050 | 0 | Idêntico ao jMetal (Legado) |
| 16 | `W2D_800.dat` | 15959 | `b6cc24b2df16d81d5238ecb48e0961bf9957e1100552aaec7775467f50d5774e` | 2 | 800 | SPACE (CRLF) | Sim (2) | 0.99999950 / 1.00000050 | 0 | Idêntico ao jMetal (Legado) |
| 17 | `W3D_100.dat` | 7600 | `a56faeb0d55a2d06236311619ad916e24651c9076b21d5ca161b3b5c642c066a` | 3 | 100 | SPACE (CRLF) | Sim (39) | 1.00000000 / 1.00000000 | 0 | Idêntico ao jMetal (Legado) |
| 18 | `W3D_1000.dat` | 29285 | `d85aa03a11276ccf888417b2734e92cdf2946123cbb6444b0af45e280a02e092` | 3 | 1000 | SPACE (CRLF) | Sim (6) | 0.99999900 / 1.00000100 | 0 | Idêntico ao jMetal (Legado) |
| 19 | `W3D_1035.dat` | 57792 | `433d67276149520cd0a026ab260373585a37e5019ff745bafc93247917093e45` | 3 | 1035 | TAB (CRLF) | Não (1e-5: 135) | 1.00000000 / 1.00002000 | 0 | Sem contraparte jMetal; 0->1e-5 (Legado) |
| 20 | `W3D_105.dat` | 5892 | `bba75570469b5eb5bcf05f9f2ccf824b70ef1c7abb7ff69d2d6f7fd946777478` | 3 | 105 | TAB (CRLF) | Não (1e-5: 42) | 1.00000000 / 1.00002000 | 0 | Sem contraparte jMetal; 0->1e-5 (Legado) |
| 21 | `W3D_120.dat` | 6729 | `48d0167ba9f61bc42924ad7718c9c839b1f00b1d3b01d43e8a771b5669fd224c` | 3 | 120 | TAB (CRLF) | Não (1e-5: 45) | 1.00000000 / 1.00002000 | 0 | Sem contraparte jMetal; 0->1e-5 (Legado) |
| 22 | `W3D_1200.dat` | 35243 | `45a4d54fcdd2d271a2cbbfc21b487d2b75fc834a0986340375899d008f606731` | 3 | 1200 | SPACE (CRLF) | Sim (6) | 0.99999900 / 1.00000100 | 0 | Idêntico ao jMetal (Legado) |
| 23 | `W3D_136.dat` | 6125 | `63638fde1f6019e1e847e92812c97b6e65ff64ee80643d469caa3649bcdb9a74` | 3 | 136 | SPACE (CRLF) | Sim (41) | 1.00000000 / 1.00000000 | 0 | Idêntico ao jMetal (Legado) |
| 24 | `W3D_150.dat` | 4374 | `c1ee8e11002d55d204a4d525c09de1e2483f228f4a070ebf5bf3fa3258d1c04c` | 3 | 150 | SPACE (CRLF) | Sim (6) | 0.99999900 / 1.00000100 | 0 | Sem contraparte jMetal; zeros preservados (Legado) |
| 25 | `W3D_300.dat` | 22800 | `6fdc6baceb4b0c9b6e25f8111ba1aeab948cd6ae67ba6c389154c5646a6624cb` | 3 | 300 | SPACE (CRLF) | Sim (72) | 1.00000000 / 1.00000000 | 0 | Idêntico ao jMetal (Legado) |
| 26 | `W3D_500.dat` | 14697 | `eb2842574578e2fc3f69b6b07fbd5b5bece023d2d351e73eea1f679162d68ab9` | 3 | 500 | SPACE (CRLF) | Sim (6) | 0.99999900 / 1.00000100 | 0 | Idêntico ao jMetal (Legado) |
| 27 | `W3D_600.dat` | 17613 | `ddae02546592453aa88ace99146a70adfc6640d29f366473aeedc09c3a91db94` | 3 | 600 | SPACE (CRLF) | Sim (6) | 0.99999900 / 1.00000100 | 0 | Idêntico ao jMetal (Legado) |
| 28 | `W3D_800.dat` | 23429 | `464b5bd769f6a3ed89461f29bb5d6f5096f2dd736a403a00f25722f89d75464a` | 3 | 800 | SPACE (CRLF) | Sim (6) | 0.99999900 / 1.00000100 | 0 | Idêntico ao jMetal (Legado) |
| 29 | `W3D_91.dat` | 3905 | `efa21ba6729b44e4c0f5eb83fb77276607736ec68700c71c3fe72871508c0b95` | 3 | 91 | SPACE (CRLF) | Sim (39) | 1.00000000 / 1.00000000 | 0 | Idêntico ao jMetal (Ativo) |
| 30 | `W3D_990.dat` | 55296 | `6348a2f15254eb9eb2b0dce06e51b9bb7920afb879c0fd7573d32e4683c72c97` | 3 | 990 | TAB (CRLF) | Não (1e-5: 132) | 1.00000000 / 1.00002000 | 0 | Sem contraparte jMetal; 0->1e-5 (Legado) |
| 31 | `W4D_120.dat` | 9128 | `eb939e5380a9913a939bee2445fd6a941df032bc5cde60f0ca85f9e5b132b67f` | 4 | 120 | TAB (CRLF) | Não (1e-5: 144) | 1.00000000 / 1.00003000 | 0 | Sem contraparte jMetal; 0->1e-5 (Legado) |
| 32 | `W4D_165.dat` | 3501 | `679396a50cd8b2ea9e08c1a11a2bf29e1c529775dce971fe4554f87750058e55` | 4 | 165 | COMMA (CRLF) | Sim (180) | 1.00000000 / 1.00000000 | 0 | Idêntico ao jMetal (Legado) |
| 33 | `W5D_100.dat` | 4767 | `baf0ef26db4320ed80c54b4abd6da1b962234819f280e23c1a747bd751b8ca5b` | 5 | 100 | SPACE (CRLF) | Sim (20) | 0.99999860 / 1.00000090 | 0 | Sem contraparte jMetal; zeros preservados (Legado) |
| 34 | `W5D_1000.dat` | 48619 | `fb2948b777c6821cacad13a19ebfd25cf218814318633a7fa2e460500c190871` | 5 | 1000 | SPACE (CRLF) | Sim (20) | 0.99999840 / 1.00000143 | 0 | Idêntico ao jMetal (Legado) |
| 35 | `W5D_1200.dat` | 58343 | `32121b818fdb96701881789a563ac47e292ea27ba500e8c78a0125decdcad05b` | 5 | 1200 | SPACE (CRLF) | Sim (20) | 0.99999800 / 1.00000143 | 0 | Idêntico ao jMetal (Legado) |
| 36 | `W5D_126.dat` | 3521 | `4eb6ea0f77d0d8aafbbc0caedb142bfc772448e29c72fb8236cecb88c77efcf7` | 5 | 126 | TAB (CRLF) | Sim (280) | 1.00000000 / 1.00000000 | 0 | Sem contraparte jMetal; zeros preservados (Legado) |
| 37 | `W5D_1500.dat` | 72912 | `bd936f2b9d846b702a47b4973b9f93d2246f68cb5951d4aecc0a0c7b022be96c` | 5 | 1500 | SPACE (CRLF) | Sim (20) | 0.99999800 / 1.00000160 | 0 | Idêntico ao jMetal (Legado) |
| 38 | `W5D_1800.dat` | 87471 | `0d20f18e9bc0cc2e57956993d82333db13033c08f16bc2e1e0d6f99aea2ae832` | 5 | 1800 | SPACE (CRLF) | Sim (20) | 0.99999800 / 1.00000160 | 0 | Idêntico ao jMetal (Legado) |
| 39 | `W5D_2000.dat` | 97172 | `47e8df6bb132ebe8ed8e93b16947d6eae5186e802e71e31b997eb93e408c395d` | 5 | 2000 | SPACE (CRLF) | Sim (20) | 0.99999800 / 1.00000160 | 0 | Idêntico ao jMetal (Legado) |
| 40 | `W5D_210.dat` | 20290 | `f3e4ca3ad0f95808d5b9736eeeda9383864f8aff397698b6585499e5f2bd3825` | 5 | 210 | TAB (CRLF) | Não (1e-5: 420) | 1.00000000 / 1.00004000 | 0 | Sem contraparte jMetal; 0->1e-5 (Ativo) |
| 41 | `W5D_2500.dat` | 121411 | `5c350fba89dd385af3647aa592a3e6e0d7f07bdfb2c035d34ea5119218e2afcd` | 5 | 2500 | SPACE (CRLF) | Sim (20) | 0.99999800 / 1.00000160 | 0 | Idêntico ao jMetal (Legado) |
| 42 | `W5D_495.dat` | 13360 | `abbefa3e9ac6fb63842a75898f423540e37c7ff6c6509fb34f5f956e0dfa7d5c` | 5 | 495 | SPACE (CRLF) | Sim (825) | 1.00000000 / 1.00000000 | 0 | Idêntico ao jMetal (Legado) |
| 43 | `W5D_715.dat` | 68285 | `139d608bc43a7cd73161c0818b9d0fe575bcea1aa4bb19580a7d752dba9f664a` | 5 | 715 | TAB (CRLF) | Não (1e-5: 1100) | 1.00000000 / 1.00004000 | 0 | Sem contraparte jMetal; 0->1e-5 (Legado) |
| 44 | `W6D_252.dat` | 29640 | `51eadf7272bd089c404fae6a33023d15e817bde0a56f2c25fdb08b8f86e7cc13` | 6 | 252 | TAB (CRLF) | Não (1e-5: 756) | 1.00001000 / 1.00005000 | 0 | Sem contraparte jMetal; 0->1e-5 (Legado) |
| 45 | `W7D_210.dat` | 29204 | `e7206ed020c13b7a77b3503cf30f120bdfe300fe11888ac0ad61cffd73a01648` | 7 | 210 | TAB (CRLF) | Não (1e-5: 882) | 1.00003000 / 1.00006000 | 0 | Sem contraparte jMetal; 0->1e-5 (Legado) |
| 46 | `W8D_120.dat` | 19288 | `b67707c6768596d92fbc5c184f79d1308800a7b6255d89aee830000416fc1a12` | 8 | 120 | TAB (CRLF) | Não (1e-5: 672) | 1.00005000 / 1.00007000 | 0 | Sem contraparte jMetal; 0->1e-5 (Legado) |
| 47 | `W8D_156.dat` | 9348 | `42f0ab3142b3fce0680fbb6505ab9d24dd27f91c1fdded47c16cb6773ec853db` | 8 | 156 | SPACE (CRLF) | Sim (896) | 1.00000000 / 1.00000000 | 8 | Idêntico ao jMetal (Ativo) |
| 48 | `W8D_330.dat` | 52762 | `308d879a2832aa23863844309a834396b7f1d20d7fadb0c4cc8874b6f689600c` | 8 | 330 | TAB (CRLF) | Não (1e-5: 1680) | 1.00004000 / 1.00007000 | 0 | Sem contraparte jMetal; 0->1e-5 (Legado) |

---

## 4. Validação Matemática dos 6 Arquivos Ativos do Pipeline

Os 6 arquivos ativos para $m \in \{2, 3, 5, 8, 10, 15\}$ foram validados em `MoeaddWeightValidationTest`:

1. **`W2D_100.dat`** ($m=2, N=100, H_1=99, H_2=0$):
   - Origem: acervo histórico `MOEAD_Weights/` (sem contraparte no jMetal oficial).
   - Formato: separado por TAB, notação científica de 15 dígitos, substituição $0 \mapsto 10^{-5}$ (2 componentes alterados nos extremos).
   - Equivalência matemática: `EQUIVALENT` ao gerador canônico Das & Dennis com substituição $0 \mapsto 10^{-5}$ (`maxCoordErr = 0.0`).
2. **`W3D_91.dat`** ($m=3, N=91, H_1=12, H_2=0$):
   - Origem: idêntico ao recurso oficial do jMetal (`jMetal/resources/weightVectorFiles/moead/W3D_91.dat`).
   - Formato: separado por SPACE, contém 39 componentes iguais a `0.0`, soma unitária exata (`maxSumDev = 1.11e-16`), 0 duplicatas.
   - Equivalência matemática: `EQUIVALENT` ao gerador canônico puro de Das & Dennis (`maxCoordErr < 1e-15`).
3. **`W5D_210.dat`** ($m=5, N=210, H_1=6, H_2=0$):
   - Origem: acervo histórico `MOEAD_Weights/` (sem contraparte no jMetal oficial).
   - Formato: separado por TAB, substituição $0 \mapsto 10^{-5}$ (420 componentes iguais a `1e-5`), 0 duplicatas.
   - Equivalência matemática: `EQUIVALENT` ao gerador canônico com substituição $0 \mapsto 10^{-5}$ (`maxCoordErr < 1e-15`).
4. **`W8D_156.dat`** ($m=8, N=156, H_1=3, H_2=2$):
   - Origem: idêntico ao recurso oficial do jMetal (`jMetal/resources/weightVectorFiles/moead/W8D_156.dat`).
   - Formato: separado por SPACE, contém 896 componentes iguais a `0.0`, soma unitária exata (`1.0`).
   - Estrutura de camadas: concatenação de $C(3+8-1,7)=120$ vetores da camada externa ($H_1=3$, coordenadas em $\{0, 1/3, 2/3, 1\}$) com $C(2+8-1,7)=36$ vetores da camada interna ($H_2=2$, coordenadas em $\{0, 0.5, 1\}$) **sem contração centrípeta**. Possui 8 duplicatas exatas correspondentes aos 8 vértices unitários extremos ($e_1, \dots, e_8$).
5. **`W10D_275.dat`** ($m=10, N=275, H_1=3, H_2=2$):
   - Origem: idêntico ao recurso oficial do jMetal (`jMetal/resources/weightVectorFiles/moead/W10D_275.dat`).
   - Formato: separado por SPACE, contém 2100 componentes iguais a `0.0`, soma unitária exata (`1.0`).
   - Estrutura de camadas: concatenação de $C(3+10-1,9)=220$ vetores ($H_1=3$) com $C(2+10-1,9)=55$ vetores ($H_2=2$) **sem contração centrípeta**. Possui 10 duplicatas exatas correspondentes aos 10 vértices unitários extremos ($e_1, \dots, e_{10}$).
6. **`W15D_135.dat`** ($m=15, N=135, H_1=2, H_2=1$):
   - Origem: acervo histórico `MOEAD_Weights/` (sem contraparte no jMetal oficial).
   - Formato: separado por TAB, substituição $0 \mapsto 10^{-5}$ antes da contração centrípeta de Deb & Jain (coordenada mínima na camada externa `1e-5` e na camada interna `0.5 * 1e-5 + 1/30 = 0.0333383333333333`), 0 duplicatas.
   - Equivalência matemática: `EQUIVALENT` ao gerador canônico de duas camadas contraídas com substituição $0 \mapsto 10^{-5}$ (`maxCoordErr < 1e-13`).

---

## 5. Prova Matemática e de Bytecode sobre Divisão por Zero ($w_i = 0$)

Para responder com rigor à questão da Parte 3 de `tasks/task11.md` (*quais algoritmos/funções dividem por zero quando $w_i = 0$*), desmontamos o bytecode real do jMetal 7.8 com `javap -c -p`:

### 5.1 `MOEA/DD`, `MOEA/D` e `MOEA/D-DRA` (`AbstractMOEAD.java` e `MOEADD.java`) — **NÃO dividem por zero**

1. **Escalarização `PBI` (`AbstractMOEAD.fitnessFunction`, offsets `173–326`):**
   - Calcula o comprimento da projeção $d_1 = \frac{|\sum_{i=1}^m (f_i(x) - z_i^*) \lambda_i|}{\|\lambda\|_2}$ e a distância ortogonal $d_2 = \| (f(x) - z^*) - d_1 \frac{\lambda}{\|\lambda\|_2} \|_2$, retornando $d_1 + \theta d_2$ ($\theta = 5.0$).
   - A única divisão utiliza a norma euclidiana do vetor $\lambda$: $\|\lambda\|_2 = \sqrt{\sum_{i=1}^m \lambda_i^2} > 0$. **Nunca há divisão por $\lambda_i$ individual**. Portanto, coordenadas $\lambda_i = 0.0$ são matematicamente exatas e numericamente seguras em `PBI`.
2. **Escalarização `TCHE` (`AbstractMOEAD.fitnessFunction`, offsets `24–112`):**
   - Implementa Tchebycheff multiplicativo com guarda explícita para $\lambda_i = 0$:
     ```java
     double diff = Math.abs(individual.objectives()[n] - idealPoint.value(n));
     double feval = (lambda[n] == 0.0) ? (0.0001 * diff) : (diff * lambda[n]);
     ```
   - No bytecode (`offset 63: dconst_0; dcmpl; ifne 76; ldc2_w 1.0E-4d; dload 7; dmul`), quando $\lambda_i = 0.0$, substitui internamente o fator multiplicador por $10^{-4}$. **Não há divisão por zero**.
3. **Escalarização `AGG` (`AbstractMOEAD.fitnessFunction`, offsets `122–165`):**
   - Soma ponderada linear $\sum_{i=1}^m \lambda_i f_i(x)$. **Não há divisão**.
4. **Distância ortogonal em `MOEADD` (`MOEADD.calculateDistance2`, offsets `0–145`):**
   - Divide exclusivamente por `norm_vector(lambda)` ($\|\lambda\|_2 > 0$). **Não há divisão por $\lambda_i$**.

### 5.2 Indicador `R2` (`ASFUtilityFunctionSet.java`) — **NÃO divide por zero**

- Em `org.uma.jmetal.qualityindicator.impl.R2`, a função de utilidade `ASFUtilityFunctionSet.evaluate` possui guarda explícita no bytecode (`offsets 200–226`):
  ```java
  double divisor = (weight > 0.0) ? weight : 0.01;
  ```
- Quando $w_i = 0.0$, divide por $0.01$. **Não produz `Infinity` nem `NaN`**.

### 5.3 `WASFGA` e `GWASFGA` (`WeightVectors.invert` + `ASFWASFGA.evaluate`) — **DIVIDEM POR ZERO quando $w_i = 0$**

- Em `org.uma.jmetal.algorithm.multiobjective.wasfga.WASFGA.createUtilityFunction()` (linha 113, herdado por `GWASFGA`), os vetores de pesos lidos do arquivo passam por:
  ```java
  weights = WeightVectors.invert(weights, true);
  ```
- O método `WeightVectors.invert(double[][] weights, boolean normalize)` (linhas 171–192 de `WeightVectors.java`, instruções `ddiv` nos offsets `47`, `81` e `119` do bytecode) calcula:
  $$\text{sum} = \sum_{j=1}^{m} \frac{1.0}{w_{i,j}}, \qquad w'_{i,j} = \frac{1.0 / w_{i,j}}{\text{sum}}$$
- Quando qualquer componente $w_{i,j} = 0.0$:
  1. `1.0 / 0.0` avalia para `Double.POSITIVE_INFINITY`;
  2. `sum` torna-se `Double.POSITIVE_INFINITY`;
  3. `(1.0 / 0.0) / sum` avalia `Infinity / Infinity = Double.NaN`!
- Em seguida, `ASFWASFGA.evaluate` multiplica as diferenças dos objetivos por `w'_{i,j} = NaN`, contaminando todo o ranking (`GWASFGARanking` / `WASFGARanking`) com `NaN`.
- **Conclusão da auditoria:** A substituição $0 \mapsto 10^{-5}$ presente nos arquivos de `NewMOEA/MOEAD_Weights` foi introduzida originalmente para permitir que algoritmos baseados em Achievement Scalarizing Function com inversão de pesos (`WASFGA` / `GWASFGA`) lessem os mesmos arquivos `.dat` sem sofrer divisão por zero (`Infinity / Infinity = NaN`). Para o `MOEA/DD` (com `PBI`), a substituição de zero não é necessária para evitar divisão por zero, embora cause apenas uma perturbação de ordem $O(10^{-5})$ quando presente.

---

## 6. Cópia Fiel e Procedimento Opcional `-DreplaceZeroWeights=false`

1. **Cópias fiéis (`resources/weightVectorFiles/`):**
   - Os 29 arquivos oficiais do jMetal foram copiados sem qualquer alteração de conteúdo, formatação ou bytes para `resources/weightVectorFiles/` e `resources/weightVectorFiles/`.
   - O teste `MoeaddWeightValidationTest.testOfficialJMetalCopiesAndSha256Integrity` verifica os 29 hashes SHA-256 e igualdade byte a byte.
2. **Propriedade JVM `-DreplaceZeroWeights=false` (desativada por padrão):**
   - Implementada em `ReferenceSetValidator.isReplaceZeroWeightsEnabled()`, `replaceZeroWeightsIfEnabled(...)` e `replaceZeroWeights(...)`.
   - Quando `false` (padrão): não grava arquivos em `resources/weightVectorFiles/weights_gen/`.
   - Quando `true`: gera variantes aplicando $0.0 \mapsto 1.0 \times 10^{-5}$ em `resources/weightVectorFiles/weights_gen/`, preservando dimensionalidade, cardinalidade e ordem dos vetores, registrando o total de componentes substituídos e o vetor de somas, e validando finitude, não negatividade e ausência de duplicatas, mantendo os arquivos originais 100% intocados.
3. **Integração com o pipeline (`WFGStudy.java`):**
   - `MOEAD_Weights/` foi mantido como diretório ativo padrão em `WFGStudy.java` (sem nenhuma edição em `WFGStudy.java`).
   - O teste `MoeaddWeightValidationTest.testSingleRunWithCopiedAndGeneratedWeightDirectories` valida 1 execução reduzida do `MOEA/DD` consumindo `resources/weightVectorFiles/weights_gen`.
