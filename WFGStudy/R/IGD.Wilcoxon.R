write("", "IGD.Wilcoxon.tex",append=FALSE)
resultDirectory<-"../data"
latexHeader <- function() {
  write("\\documentclass{article}", "IGD.Wilcoxon.tex", append=TRUE)
  write("\\title{StandardStudy}", "IGD.Wilcoxon.tex", append=TRUE)
  write("\\usepackage{amssymb}", "IGD.Wilcoxon.tex", append=TRUE)
  write("\\author{A.J.Nebro}", "IGD.Wilcoxon.tex", append=TRUE)
  write("\\begin{document}", "IGD.Wilcoxon.tex", append=TRUE)
  write("\\maketitle", "IGD.Wilcoxon.tex", append=TRUE)
  write("\\section{Tables}", "IGD.Wilcoxon.tex", append=TRUE)
  write("\\", "IGD.Wilcoxon.tex", append=TRUE)
}

latexTableHeader <- function(problem, tabularString, latexTableFirstLine) {
  write("\\begin{table}", "IGD.Wilcoxon.tex", append=TRUE)
  write("\\caption{", "IGD.Wilcoxon.tex", append=TRUE)
  write(problem, "IGD.Wilcoxon.tex", append=TRUE)
  write(".IGD.}", "IGD.Wilcoxon.tex", append=TRUE)

  write("\\label{Table:", "IGD.Wilcoxon.tex", append=TRUE)
  write(problem, "IGD.Wilcoxon.tex", append=TRUE)
  write(".IGD.}", "IGD.Wilcoxon.tex", append=TRUE)

  write("\\centering", "IGD.Wilcoxon.tex", append=TRUE)
  write("\\setlength\\tabcolsep{1pt}", "IGD.Wilcoxon.tex", append=TRUE)
  write("\\begin{scriptsize}", "IGD.Wilcoxon.tex", append=TRUE)
  write("\\begin{tabular}{", "IGD.Wilcoxon.tex", append=TRUE)
  write(tabularString, "IGD.Wilcoxon.tex", append=TRUE)
  write("}", "IGD.Wilcoxon.tex", append=TRUE)
  write(latexTableFirstLine, "IGD.Wilcoxon.tex", append=TRUE)
  write("\\hline ", "IGD.Wilcoxon.tex", append=TRUE)
}

printTableLine <- function(indicator, algorithm1, algorithm2, i, j, problem) { 
  file1<-paste(resultDirectory, algorithm1, sep="/")
  file1<-paste(file1, problem, sep="/")
  file1<-paste(file1, indicator, sep="/")
  data1<-scan(file1)
  file2<-paste(resultDirectory, algorithm2, sep="/")
  file2<-paste(file2, problem, sep="/")
  file2<-paste(file2, indicator, sep="/")
  data2<-scan(file2)
  if (i == j) {
    write("-- ", "IGD.Wilcoxon.tex", append=TRUE)
  }
  else if (i < j) {
    if (is.finite(wilcox.test(data1, data2)$p.value) & wilcox.test(data1, data2)$p.value <= 0.05) {
      if (median(data1) <= median(data2)) {
        write("$\\blacktriangle$", "IGD.Wilcoxon.tex", append=TRUE)
}
      else {
        write("$\\triangledown$", "IGD.Wilcoxon.tex", append=TRUE)
}
    }
    else {
      write("--", "IGD.Wilcoxon.tex", append=TRUE)
    }
  }
  else {
    write(" ", "IGD.Wilcoxon.tex", append=TRUE)
  }
}

latexTableTail <- function() { 
  write("\\hline", "IGD.Wilcoxon.tex", append=TRUE)
  write("\\end{tabular}", "IGD.Wilcoxon.tex", append=TRUE)
  write("\\end{scriptsize}", "IGD.Wilcoxon.tex", append=TRUE)
  write("\\end{table}", "IGD.Wilcoxon.tex", append=TRUE)
}

latexTail <- function() { 
  write("\\end{document}", "IGD.Wilcoxon.tex", append=TRUE)
}

### START OF SCRIPT 
# Constants
problemList <-c("WFG1_2") 
algorithmList <-c("NSGAII", "SPEA2", "MOEADDRA", "NSGAIII", "MOEADD", "FDEA", "RVEA", "AGE-MOEA", "AGE-MOEA-II", "ESPEA", "GWASFGA") 
tabularString <-c("lcccccccccc") 
latexTableFirstLine <-c("\\hline  & SPEA2 & MOEADDRA & NSGAIII & MOEADD & FDEA & RVEA & AGE-MOEA & AGE-MOEA-II & ESPEA & GWASFGA\\\\ ") 
indicator<-"IGD"

 # Step 1.  Writes the latex header
latexHeader()
tabularString <-c("| l | c | c | c | c | c | c | c | c | c | c | ") 

latexTableFirstLine <-c("\\hline \\multicolumn{1}{|c|}{} & \\multicolumn{1}{c|}{SPEA2} & \\multicolumn{1}{c|}{MOEADDRA} & \\multicolumn{1}{c|}{NSGAIII} & \\multicolumn{1}{c|}{MOEADD} & \\multicolumn{1}{c|}{FDEA} & \\multicolumn{1}{c|}{RVEA} & \\multicolumn{1}{c|}{AGE-MOEA} & \\multicolumn{1}{c|}{AGE-MOEA-II} & \\multicolumn{1}{c|}{ESPEA} & \\multicolumn{1}{c|}{GWASFGA} \\\\") 

# Step 3. Problem loop 
latexTableHeader("WFG1_2 ", tabularString, latexTableFirstLine)

indx = 0
for (i in algorithmList) {
  if (i != "GWASFGA") {
    write(i , "IGD.Wilcoxon.tex", append=TRUE)
    write(" & ", "IGD.Wilcoxon.tex", append=TRUE)

    jndx = 0
    for (j in algorithmList) {
      for (problem in problemList) {
        if (jndx != 0) {
          if (i != j) {
            printTableLine(indicator, i, j, indx, jndx, problem)
          }
          else {
            write("  ", "IGD.Wilcoxon.tex", append=TRUE)
          } 
          if (problem == "WFG1_2") {
            if (j == "GWASFGA") {
              write(" \\\\ ", "IGD.Wilcoxon.tex", append=TRUE)
            } 
            else {
              write(" & ", "IGD.Wilcoxon.tex", append=TRUE)
            }
          }
     else {
    write("&", "IGD.Wilcoxon.tex", append=TRUE)
     }
        }
      }
      jndx = jndx + 1
}
    indx = indx + 1
  }
} # for algorithm

  latexTableTail()

#Step 3. Writes the end of latex file 
latexTail()

