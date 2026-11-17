/*
 * Calcula o Hipervolume utilizando um Método Monte Carlo
 * Obs1.: recebe como parâmetro o tamanho da amostra, caso contrário usa o default de 1000000
 * Obs2.: O arquivo prefixo.txt deve estar no diretório corrente e conter os prefixos dos nomes das instâncias
 * Obs3.: O cálculo do Hipervolume considera um conjunto de referência, portanto quanto menor o valor
 * obtido, melhor (mantém o padrão do PISA)
 */
//package hipervolumeamostragem;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Random;

/**
 *
 * @author Richard A. Gonçalves e Carolina Paula de Almeida
 */
public class Main {

	private static final Random random = new Random(System.nanoTime());
	private static int tamanhoAmostra=1000000;
	private static double refPoint;

	/**
		* @param args the command line arguments
		*/
	public static void main(String[] args) throws Exception {
		if(args.length < 2){
			System.out.println("use: hv <file> <ref_point>");
			System.exit(1);
		}
		refPoint=Double.parseDouble(args[1]);
		leArquivo(args[0]);
	}

	//le os arquivos com os conjuntos de aproximação ou referência no formato utilizado/gerado pelo PISA...
	public static void leArquivo(String nomeArq) throws IOException {
		FileReader fr = new FileReader(nomeArq);
		BufferedReader br = new BufferedReader(fr);

		ArrayList<Double> hipervolumes = new ArrayList<Double>();

		double hipervolume;

		ArrayList<double[]> dados = new ArrayList<double[]>();
		int cont = 0;

		String linha = br.readLine();
		while (linha != null) {
				String[] objs = linha.split("\\s");
				if ((objs.length != 1 || !(objs[0].equals(""))) && !objs[0].equals("#") ) {
					double[] d = new double[objs.length];
					for (int i = 0; i < objs.length; i++) {
						d[i] = Double.parseDouble(objs[i].replace(",","."));
					}
					dados.add(d);
				} else {
					if(dados.size() > 0){
						cont++;
						System.out.print("hv("+cont+") = ");
						System.out.println(calculaHipervolumeAmostragem(dados));
						dados.clear();
					}
				}

			linha = br.readLine();
		}

		//Calcula o hipervolume para cada conjunto de aproximação...
		if(dados.size() > 0){
			cont++;
			System.out.print("hv("+cont+") = ");
			System.out.println(calculaHipervolumeAmostragem(dados));
			dados.clear();
		}
		fr.close();
		br.close();
	}

	//Calcula o hipervolume por Monte Carlo
	//Obs1. : Supõe que os dados estão normalizados seguindo o padrão do PISA, ou seja,
	//no intervalo [1.0,2.1]
	public static double calculaHipervolumeAmostragem(ArrayList<double[]> dados) throws IOException{
		int dimensao = dados.get(0).length;
		int contaAcertos = 0;
		double gerado[] = new double[dimensao];
		// Modificar a linha abaixo caso os dados não estejam normalizados
		// ou estejam normalizados em um intervalo de tamanho diferente
		// 1.1 = 2.1 - 1.0, ou seja, limite superior - limite inferior

		//no meu caso 1.0 = 1 - 0

		// Se os dados tiverem diferentes limites para os diferentes objetivos
		// calcular o volumeTotal pelo produtório das diferenças dos limites....
		double volumeTotal = Math.pow(refPoint, dimensao);
		boolean dominado = true;


		for (int i = 0; i < tamanhoAmostra; i++) {
			for (int j = 0; j < dimensao; j++) {
				//Gera cada dimensao de um ponto...
				//gerado[j] = 1.0 + 1.1 * random.nextDouble();
				gerado[j] = refPoint * random.nextDouble();
				//limites uniformes...
				//gerado[j] = limiteInferior + (limiteSuperior - limiteInferior) * random.nextDouble();
				//diferente limites para cada dimensao...
				//gerado[j] = limiteInferior[j] + (limiteSuperior[j] - limiteInferior[j]) * random.nextDouble();
			}

			dominado = false;

			//Verifica se o ponto é dominado ou não...
			for (int k = 0; ((k < dados.size()) && (!dominado)); k++) {
				double[] temp = dados.get(k);
				boolean dominadoTemp = true;

				for (int d = 0; d < dimensao; d++) {
					if (temp[d] > gerado[d]) {
						dominadoTemp = false;
					}
				}

				if (dominadoTemp) {
					dominado = true;
				}
			}

			if (dominado) {
				contaAcertos++;//conta o número de pontos dominados...
			}

			//if(i%1000 == 0)
			//System.out.println(i);
		}





		/***************************************************/

		// ArrayList<double[]> ref=lerReal("pareto/REF_HV_"+dimensao);
		// for (int i = 0; i < ref.size(); i++) {

		// 	for (int j = 0; j < dimensao; j++) {
		// 		gerado[j] = refPoint * ref.get(i)[j];
		// 	}
		// 	dominado = false;

		// 	//Verifica se o ponto é dominado ou não...
		// 	for (int k = 0; ((k < dados.size()) && (!dominado)); k++) {
		// 		double[] temp = dados.get(k);
		// 		boolean dominadoTemp = true;

		// 		for (int d = 0; d < dimensao; d++) {
		// 			if (temp[d] > gerado[d]) {
		// 				dominadoTemp = false;
		// 			}
		// 		}

		// 		if (dominadoTemp) {
		// 			dominado = true;
		// 		}
		// 	}

		// 	if (dominado) {
		// 		contaAcertos++;//conta o número de pontos dominados...
		// 	}
		// }


		/***************************************************/

		//System.out.println(contaAcertos);

		//O hipervolume é proporcional a quantidade de pontos dominados e ao volume da área amostrada...
		return (double) contaAcertos / (double) tamanhoAmostra * volumeTotal;
		//return (double) contaAcertos / (double) (tamanhoAmostra+ref.size()) * volumeTotal;

	}

	public static ArrayList<double[]> lerReal(String arquivo) throws IOException{
		FileReader fr = new FileReader(arquivo);
		BufferedReader br = new BufferedReader(fr);
		ArrayList<double[]> dados = new ArrayList<double[]>();
		int cont = 0;

		String linha = br.readLine();
		while (linha != null) {
			String[] objs = linha.split("\\s");
			if(objs.length==0)
				objs = linha.split("\t");
			if ((objs.length != 1 || !(objs[0].equals(""))) && !objs[0].equals("#") ) {
				double[] d = new double[objs.length];
				for (int i = 0; i < objs.length; i++) {
					d[i] = Double.parseDouble(objs[i].replace(",","."));
				}
				dados.add(d);
			} else {
				if(dados.size() > 0)
					//System.out.println(calcular(dados));
					return dados;

				dados.clear();
			}
			linha = br.readLine();
		}
//		if(dados.size() > 0)
			return dados;
	}
}
