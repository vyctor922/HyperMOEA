postscript("IGD+.Boxplot.eps", horizontal=FALSE, onefile=FALSE, height=8, width=12, pointsize=10)
resultDirectory<-"../data"
qIndicator <- function(indicator, problem)
{
fileNSGAII<-paste(resultDirectory, "NSGAII", sep="/")
fileNSGAII<-paste(fileNSGAII, problem, sep="/")
fileNSGAII<-paste(fileNSGAII, indicator, sep="/")
NSGAII<-scan(fileNSGAII)

fileSPEA2<-paste(resultDirectory, "SPEA2", sep="/")
fileSPEA2<-paste(fileSPEA2, problem, sep="/")
fileSPEA2<-paste(fileSPEA2, indicator, sep="/")
SPEA2<-scan(fileSPEA2)

fileMOEADDRA<-paste(resultDirectory, "MOEADDRA", sep="/")
fileMOEADDRA<-paste(fileMOEADDRA, problem, sep="/")
fileMOEADDRA<-paste(fileMOEADDRA, indicator, sep="/")
MOEADDRA<-scan(fileMOEADDRA)

fileNSGAIII<-paste(resultDirectory, "NSGAIII", sep="/")
fileNSGAIII<-paste(fileNSGAIII, problem, sep="/")
fileNSGAIII<-paste(fileNSGAIII, indicator, sep="/")
NSGAIII<-scan(fileNSGAIII)

fileMOEADD<-paste(resultDirectory, "MOEADD", sep="/")
fileMOEADD<-paste(fileMOEADD, problem, sep="/")
fileMOEADD<-paste(fileMOEADD, indicator, sep="/")
MOEADD<-scan(fileMOEADD)

fileFDEA<-paste(resultDirectory, "FDEA", sep="/")
fileFDEA<-paste(fileFDEA, problem, sep="/")
fileFDEA<-paste(fileFDEA, indicator, sep="/")
FDEA<-scan(fileFDEA)

fileRVEA<-paste(resultDirectory, "RVEA", sep="/")
fileRVEA<-paste(fileRVEA, problem, sep="/")
fileRVEA<-paste(fileRVEA, indicator, sep="/")
RVEA<-scan(fileRVEA)

fileAGE-MOEA<-paste(resultDirectory, "AGE-MOEA", sep="/")
fileAGE-MOEA<-paste(fileAGE-MOEA, problem, sep="/")
fileAGE-MOEA<-paste(fileAGE-MOEA, indicator, sep="/")
AGE-MOEA<-scan(fileAGE-MOEA)

fileAGE-MOEA-II<-paste(resultDirectory, "AGE-MOEA-II", sep="/")
fileAGE-MOEA-II<-paste(fileAGE-MOEA-II, problem, sep="/")
fileAGE-MOEA-II<-paste(fileAGE-MOEA-II, indicator, sep="/")
AGE-MOEA-II<-scan(fileAGE-MOEA-II)

fileESPEA<-paste(resultDirectory, "ESPEA", sep="/")
fileESPEA<-paste(fileESPEA, problem, sep="/")
fileESPEA<-paste(fileESPEA, indicator, sep="/")
ESPEA<-scan(fileESPEA)

fileGWASFGA<-paste(resultDirectory, "GWASFGA", sep="/")
fileGWASFGA<-paste(fileGWASFGA, problem, sep="/")
fileGWASFGA<-paste(fileGWASFGA, indicator, sep="/")
GWASFGA<-scan(fileGWASFGA)

algs<-c("NSGAII","SPEA2","MOEADDRA","NSGAIII","MOEADD","FDEA","RVEA","AGE-MOEA","AGE-MOEA-II","ESPEA","GWASFGA")
boxplot(NSGAII,SPEA2,MOEADDRA,NSGAIII,MOEADD,FDEA,RVEA,AGE-MOEA,AGE-MOEA-II,ESPEA,GWASFGA,names=algs, notch = TRUE)
titulo <-paste(indicator, problem, sep=":")
title(main=titulo)
}
par(mfrow=c(3,3))
indicator<-"IGD+"
qIndicator(indicator, "WFG1_2")
