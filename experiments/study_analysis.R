#install.packages("chron")

library(lubridate)
library(chron)
library(plotrix)
setwd("~/Documents/GitHub/CS5099/PicrossGeneratorSolver/participants_instances")
getwd()

data <- read.table(file="collected_data.csv", sep = ",", as.is = TRUE, header = TRUE, stringsAsFactors=FALSE)
data
data[5]
data[, 5]
data$X7x7_70_LC
typeof(data[2, 5])

# to access column "X7x7_70_LC" we can do either: "data[, 5]" or "data$X7x7_70_LC"
level_1_seconds<-60 * 60 * 24 * as.numeric(times(data[, 5]))
level_1_seconds

list_exp_level_1 <- subset(data, Experienced=='Yes')$X7x7_70_LC
list_exp_level_1_seconds <- 60 * 60 * 24 * as.numeric(times(list_exp_level_1))
list_exp_level_1_seconds


list_not_exp_level_1 <-subset(data, Experienced=='No')$X7x7_70_LC
list_not_exp_level_1 <- 60 * 60 * 24 * as.numeric(times(list_not_exp_level_1))
list_not_exp_level_1

# The mean value of time spent on the first round of the people who played tha game is smaller than those who had no epxerience before is bigger than

get_avg_time_exp_level1 <- function(all_data){
  filtered_list_exp_level1 <- c()
  list_NC <- c()
  list_failed <- c()
  list_exp_level_1 <- subset(all_data, Experienced=='Yes')$X7x7_70_LC
  list_exp_level_1_seconds <- 60 * 60 * 24 * as.numeric(times(list_exp_level_1))
  for (time in list_exp_level_1_seconds){
    if(time<360){
      filtered_list_exp_level1 <- c(filtered_list_exp_level1, time)
    }else if(time == 360){
      list_NC <- c(list_NC, time)
    }else if(time == 420){
      list_failed <- c(list_failed, time)
    }
  }
  print(length(list_NC))
  return(list(filtered_list_exp_level1, length(list_NC), length(list_failed)))
}

get_avg_time_nonexp_level1 <- function(all_data){
  filtered_list_n_exp_level1 <- c()
  list_NC <- c()
  list_failed <- c()
  list_not_exp_level_1 <-subset(data, Experienced=='No')$X7x7_70_LC
  list_not_exp_level_1_seconds <- 60 * 60 * 24 * as.numeric(times(list_not_exp_level_1))
  for (time in list_not_exp_level_1_seconds){
    if(time<360){
      filtered_list_n_exp_level1 <- c(filtered_list_n_exp_level1, time)
    }else if(time == 360){
      list_NC <- c(list_NC, time)
    }else if(time == 420){
      list_failed <- c(list_failed, time)
    }
  }
  return(list(filtered_list_n_exp_level1, list_NC, list_failed))
}


list_exp <- get_avg_time_exp_level1(data)
length(list_exp)
length(list_exp[[1]])
length(list_exp[1][1])
filtered_avg_time_exp_level1 <- list_exp[1]
amount_NC_exp_level1 <- list_exp[2]
amount_Failed_exp_level1 <- list_exp[3]
filtered_avg_time_exp_level1
amount_NC_exp_level1[1][1][1]
typeof(amount_NC_exp_level1)
amount_NC_exp_level1[2]
amount_Failed_exp_level1

list_n_exp <- get_avg_time_nonexp_level1(data)
list_n_exp
filtered_avg_time_nonexp_level1 <- list_n_exp[1]
amount_NC_nonexp_level1 <- list_n_exp[2]
amount_Failed_nonexp_level1 <- list_n_exp[3]
filtered_avg_time_nonexp_level1
amount_NC_nonexp_level1
amount_Failed_nonexp_level1

length(filtered_avg_time_nonexp_level1)
length(amount_NC_nonexp_level1)
length(amount_Failed_nonexp_level1)


typeof(amount_NC_exp_level1)
amount_NC_exp_level1[2]
amount_Failed_exp_level1

list_exp[1,1]
typeof(list_exp[1])
typeof(list_exp[2])
list_exp[1, 2]
length(list_exp[2])

filtered_avg_time_exp_level1
amount_NC_exp_level1 
amount_Failed_exp_level1
filtered_avg_time_nonexp_level1 <- list_exp[1]

list_exp[1]
list_exp[1]

mean(list_exp)
mean(list_n_exp)
summary(list_exp)
summary(list_n_exp)


