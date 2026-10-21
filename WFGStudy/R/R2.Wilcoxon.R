write("", "R2.Wilcoxon.tex",append=FALSE)
resultDirectory<-"../data"
latexHeader <- function() {
  write("\\documentclass{article}", "R2.Wilcoxon.tex", append=TRUE)
  write("\\title{StandardStudy}", "R2.Wilcoxon.tex", append=TRUE)
  write("\\usepackage{amssymb}", "R2.Wilcoxon.tex", append=TRUE)
  write("\\author{A.J.Nebro}", "R2.Wilcoxon.tex", append=TRUE)
  write("\\begin{document}", "R2.Wilcoxon.tex", append=TRUE)
  write("\\maketitle", "R2.Wilcoxon.tex", append=TRUE)
  write("\\section{Tables}", "R2.Wilcoxon.tex", append=TRUE)
  write("\\", "R2.Wilcoxon.tex", append=TRUE)
}

latexTableHeader <- function(problem, tabularString, latexTableFirstLine) {
  write("\\begin{table}", "R2.Wilcoxon.tex", append=TRUE)
  write("\\caption{", "R2.Wilcoxon.tex", append=TRUE)
  write(problem, "R2.Wilcoxon.tex", append=TRUE)
  write(".R2.}", "R2.Wilcoxon.tex", append=TRUE)

  write("\\label{Table:", "R2.Wilcoxon.tex", append=TRUE)
  write(problem, "R2.Wilcoxon.tex", append=TRUE)
  write(".R2.}", "R2.Wilcoxon.tex", append=TRUE)

  write("\\centering", "R2.Wilcoxon.tex", append=TRUE)
  write("\\setlength\\tabcolsep{1pt}", "R2.Wilcoxon.tex", append=TRUE)
  write("\\begin{scriptsize}", "R2.Wilcoxon.tex", append=TRUE)
  write("\\begin{tabular}{", "R2.Wilcoxon.tex", append=TRUE)
  write(tabularString, "R2.Wilcoxon.tex", append=TRUE)
  write("}", "R2.Wilcoxon.tex", append=TRUE)
  write(latexTableFirstLine, "R2.Wilcoxon.tex", append=TRUE)
  write("\\hline ", "R2.Wilcoxon.tex", append=TRUE)
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
    write("-- ", "R2.Wilcoxon.tex", append=TRUE)
  }
  else if (i < j) {
    if (is.finite(wilcox.test(data1, data2)$p.value) & wilcox.test(data1, data2)$p.value <= 0.05) {
      if (median(data1) <= median(data2)) {
        write("$\\blacktriangle$", "R2.Wilcoxon.tex", append=TRUE)
}
      else {
        write("$\\triangledown$", "R2.Wilcoxon.tex", append=TRUE)
}
    }
    else {
      write("--", "R2.Wilcoxon.tex", append=TRUE)
    }
  }
  else {
    write(" ", "R2.Wilcoxon.tex", append=TRUE)
  }
}

latexTableTail <- function() { 
  write("\\hline", "R2.Wilcoxon.tex", append=TRUE)
  write("\\end{tabular}", "R2.Wilcoxon.tex", append=TRUE)
  write("\\end{scriptsize}", "R2.Wilcoxon.tex", append=TRUE)
  write("\\end{table}", "R2.Wilcoxon.tex", append=TRUE)
}

latexTail <- function() { 
  write("\\end{document}", "R2.Wilcoxon.tex", append=TRUE)
}

### START OF SCRIPT 
# Constants
problemList <-c("WFG1_2") 
algorithmList <-c("NSGAII", "SPEA2", "MOEADDRA", "NSGAIII", "MOEADD", "FDEA", "RVEA", "AGE-MOEA", "AGE-MOEA-II", "ESPEA", "GWASFGA") 
tabularString <-c("lcccccccccc") 
latexTableFirstLine <-c("\\hline  & SPEA2 & MOEADDRA & NSGAIII & MOEADD & FDEA & RVEA & AGE-MOEA & AGE-MOEA-II & ESPEA & GWASFGA\\\\ ") 
indicator<-"R2"

 # Step 1.  Writes the latex header
latexHeader()
tabularString <-c("| l | c | c | c | c | c | c | c | c | c | c | ") 

latexTableFirstLine <-c("\\hline \\multicolumn{1}{|c|}{} & \\multicolumn{1}{c|}{SPEA2} & \\multicolumn{1}{c|}{MOEADDRA} & \\multicolumn{1}{c|}{NSGAIII} & \\multicolumn{1}{c|}{MOEADD} & \\multicolumn{1}{c|}{FDEA} & \\multicolumn{1}{c|}{RVEA} & \\multicolumn{1}{c|}{AGE-MOEA} & \\multicolumn{1}{c|}{AGE-MOEA-II} & \\multicolumn{1}{c|}{ESPEA} & \\multicolumn{1}{c|}{GWASFGA} \\\\") 

# Step 3. Problem loop 
latexTableHeader("WFG1_2 ", tabularString, latexTableFirstLine)

indx = 0
for (i in algorithmList) {
  if (i != "GWASFGA") {
    write(i , "R2.Wilcoxon.tex", append=TRUE)
    write(" & ", "R2.Wilcoxon.tex", append=TRUE)

    jndx = 0
    for (j in algorithmList) {
      for (problem in problemList) {
        if (jndx != 0) {
          if (i != j) {
            printTableLine(indicator, i, j, indx, jndx, problem)
          }
          else {
            write("  ", "R2.Wilcoxon.tex", append=TRUE)
          } 
          if (problem == "WFG1_2") {
            if (j == "GWASFGA") {
              write(" \\\\ ", "R2.Wilcoxon.tex", append=TRUE)
            } 
            else {
              write(" & ", "R2.Wilcoxon.tex", append=TRUE)
            }
          }
     else {
    write("&", "R2.Wilcoxon.tex", append=TRUE)
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

