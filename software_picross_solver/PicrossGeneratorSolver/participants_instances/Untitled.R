# calculates how many instances did not have a solution
nrow(filtered_data[filtered_data$SolverSolutionsFound == 0,])/2

# removes instances without solutions
filtered_data <- filtered_data[filtered_data$SolverSolutionsFound != 0,]

# separates data by algorithm used
alg1<-subset(filtered_data, filtered_data$UUID==filtered_data$UUID[1])
alg2<-subset(filtered_data, filtered_data$UUID==filtered_data$UUID[nrow(filtered_data)])

# shows how many times each algorithm had to run before all solutions for solvable instances have been found.
nrow(alg1)
# [1] 126
nrow(alg2)
# [1] 169

# splits data by instances
alg1 <- split(alg1, alg1$Filenames)
alg2 <- split(alg2, alg2$Filenames)

# finds the mean number of iteration on an instance each algorithm required to finish
mean(unlist(lapply(alg1, function(x) length(x$SolverSolutionsFound))))
# [1] 1.26
mean(unlist(lapply(alg2, function(x) length(x$SolverSolutionsFound))))
# [1] 1.69

# amount of instances that each algorithm was not able to solve 
length(Filter(function(x) min(x$SolverSolutionsFound) != 1, alg1))
# [1] 39
length(Filter(function(x) min(x$SolverSolutionsFound) != 1, alg2))
# [1] 30




 






setwd("~/Documents/GitHub/CS5099/PicrossGeneratorSolver/stat_data")
getwd()
filtered_data<-read.csv("filtered.csv", stringsAsFactors=FALSE)
filtered_data
filtered_data1 <- filtered_data[, 1:14]
filtered_data1
nrow(filtered_data1)
filtered_data1$UUID[1]
subset1<-subset(filtered_data1, filtered_data1$UUID==filtered_data1$UUID[1])
subset2<-subset(filtered_data1, filtered_data1$UUID==filtered_data1$UUID[nrow(filtered_data1)])

subset1
subset2
nrow(subset1)
nrow(subset2)

subset2<-subset(filtered_data1, filtered_data1$UUID==filtered_data1$UUID[nrow(filtered_data1)])



split_by_id <- split(filtered_data1, filtered_data1$UUID)
split_by_id
length(split_by_id)

filtered_data <- info_file_data[, 1:14]
filtered_data
split_by_id <- split(filtered_data, filtered_data$UUID)
split_by_id
length(split_by_id)


info_file_data<-read.csv("infoFile.csv", stringsAsFactors=FALSE)
info_file_data


# setwd("~/Desktop/stat_data")
# getwd()
# 
# test_file <-read.csv("constInfoFile.csv", stringsAsFactors=FALSE)
# test_file


attach(info_file_data)
nrow(info_file_data)
ncol(info_file_data)
filtered_data <- info_file_data[, 1:14]
filtered_data
split_by_id <- split(filtered_data, filtered_data$UUID)
split_by_id
length(split_by_id)


ncol(filtered_data)
nrow(filtered_data)
split_by_id <- split(filtered_data, filtered_data$UUID)
split_by_id
length(split_by_id)
length(split_by_id)

subset()
split_by_id[65]
length(split_by_id[65])
length(split_by_id[65]$`f73ba037-ab60-4f21-8edf-6c5f06067f21`$Filenames)
length(split_by_id[64]$`f41a468f-6b9c-4ab9-b0c9-e370335d9d4c`$Filenames)


my.data <- filtered_data[nrow(filtered_data):1,]
my.data
length(my.data)
nrow(my.data)
ncol(my.data)
last_id <-my.data$UUID[1]
length(my.data)
last_id <-my.data$UUID[2]
my.data$UUID[10] ==my.data$UUID[1]
i<-1
i
while(TRUE){
  if(my.data$UUID[i] != my.data$UUID[1]){
    return("stopped")
  }
  i<-i+1
}

i
my.data$UUID[2]
as.data.frame(table(my.data$UUID))


info_file_data$UUID
grou_by_ids <-split(info_file_data, info_file_data$UUID)
grou_by_ids
nrow(info_file_data)
length(info_file_data)
info_file_data[14]
grou_by_ids[1]
length(grou_by_ids)
import pandas as pd
