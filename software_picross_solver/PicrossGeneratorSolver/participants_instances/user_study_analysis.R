#install.packages("chron")
#install.packages("psych")
library(chron) # for times() function
library(lubridate) # for converting seconds to periods Hours, Minutes, Seconds


#----------------------------------------------------------------------------------------------------------------
# Getting the table of data.
setwd("~/Documents/GitHub/CS5099/PicrossGeneratorSolver/participants_instances")
getwd()
data <- read.table(file="collected_data.csv", sep = ",", as.is = TRUE, header = TRUE, stringsAsFactors=FALSE)
data
attach(data)
data
#----------------------------------------------------------------------------------------------------------------
#All functions: count total time; Filtering functions:

count_total_time<-function(all_data){
  list_success <- c()
  i<-5
  while(i<=13){
    col_seconds <- 60 * 60 * 24 * as.numeric(times(all_data[, i]))
    for (time in col_seconds){
      if(time<360){
        list_success <- c(list_success, time)
      }else{
        list_success <- c(list_success, 300)
      }
    }
    i <- i+2
  }
  total_time_seconds <- sum(list_success)
  return(total_time_seconds)
}
#------------------------------------
# OUTPUT:
total_seconds <- count_total_time(data)
total_seconds
seconds_to_period(total_seconds)

#----------------------------------------------------------------------------------------------------------------

get_filtered_lists_of_time_exp <- function(all_data, is_experienced, column_index){
  list_success <- c()
  list_NC <- c()
  list_failed <- c()
  
  entire_list <- subset(all_data, Experienced==is_experienced)
  entire_list <- entire_list[, column_index]
  entire_list_seconds <- 60 * 60 * 24 * as.numeric(times(entire_list))
  for (time in entire_list_seconds){
    if(time<360){
      list_success <- c(list_success, time)
    }else if(time == 360){
      list_NC <- c(list_NC, time)
    }else if(time == 420){
      list_failed <- c(list_failed, time)
    }
  }
  return(list(list_success, list_NC, list_failed))
}


get_filtered_lists_of_time_total<- function(all_data, column_index){
  list_success <- c()
  list_NC <- c()
  list_failed <- c()
  
  entire_list <- all_data[, column_index]
  entire_list_seconds <- 60 * 60 * 24 * as.numeric(times(entire_list))
  for (time in entire_list_seconds){
    if(time<360){
      list_success <- c(list_success, time)
    }else if(time == 360){
      list_NC <- c(list_NC, time)
    }else if(time == 420){
      list_failed <- c(list_failed, time)
    }
  }
  return(list(list_success, list_NC, list_failed))
}

# Pie chart of experienced users:
draw_pie_chart <- function(list_of_lists, message){
  tot_length <- length(list_of_lists)
  i <- 1
  slices <- c()
  lbls <- c()
  while (i <= tot_length){
    if(length(list_of_lists[[i]]) > 0){
      slices <- c(slices, length(list_of_lists[[i]]))
      if(i==1){
        lbls <- c(lbls, "Success")
      }else if(i == 2){
        lbls <- c(lbls, "Not completed")
      }else if(i == 3){
        lbls <- c(lbls, "Failed")
      }
    }
    i <- i+1
  }
  pct <- round(slices/sum(slices)*100)
  lbls <- paste(lbls, slices, "people", pct)
  lbls <- paste(lbls, "%", sep = "")
  pie(slices, labels = lbls, explode = 0.1, col = c("red", "blue", "green"),  main = message)
}
#----------------------------------------------------------------------------------------------------------------

# return 3 lists for Experienced users: 
# 1. list of times, which were successfully finished,
# 2. list of not completed
# 3. list of failed


####### Gather all data here: for all levels: 1-5
list_exp_L1 <- get_filtered_lists_of_time_exp(data, "Yes", 5)
list_nonexp_L1 <- get_filtered_lists_of_time_exp(data, "No", 5)
list_total_L1 <- get_filtered_lists_of_time_total(data, 5)

list_exp_L2 <- get_filtered_lists_of_time_exp(data, "Yes", 7)
list_nonexp_L2 <- get_filtered_lists_of_time_exp(data, "No", 7)
list_total_L2 <- get_filtered_lists_of_time_total(data, 7)

list_exp_L3 <- get_filtered_lists_of_time_exp(data, "Yes", 9)
list_nonexp_L3 <- get_filtered_lists_of_time_exp(data, "No", 9)
list_total_L3 <- get_filtered_lists_of_time_total(data, 9)

list_exp_L4 <- get_filtered_lists_of_time_exp(data, "Yes", 11)
list_nonexp_L4 <- get_filtered_lists_of_time_exp(data, "No", 11)
list_total_L4 <- get_filtered_lists_of_time_total(data, 11)

list_exp_L5 <- get_filtered_lists_of_time_exp(data, "Yes", 13)
list_nonexp_L5 <- get_filtered_lists_of_time_exp(data, "No", 13)
list_total_L5 <- get_filtered_lists_of_time_total(data, 13)

#print out success lists in each level
list_total_L1[[1]]
list_total_L2[[1]]
list_total_L3[[1]]
list_total_L4[[1]]
list_total_L5[[1]]

### now drawing all :
####Level 1:
draw_pie_chart(list_exp_L1, "Pie chart of Experienced users: Level 1")
draw_pie_chart(list_nonexp_L1, "Pie chart of Not Experienced users: Level 1")
draw_pie_chart(list_total_L1, "Pie chart of all users: Level 1")
####Level 2:
draw_pie_chart(list_exp_L2, "Pie chart of Experienced users: Level 2")
draw_pie_chart(list_nonexp_L2, "Pie chart of Not Experienced users: Level 2")
draw_pie_chart(list_total_L2, "Pie chart of all users: Level 2")
####Level 3:
draw_pie_chart(list_exp_L3, "Pie chart of Experienced users: Level 3")
draw_pie_chart(list_nonexp_L3, "Pie chart of Not Experienced users: Level 3")
draw_pie_chart(list_total_L3, "Pie chart of all users: Level 3")
####Level 4:
draw_pie_chart(list_exp_L4, "Pie chart of Experienced users: Level 4")
draw_pie_chart(list_nonexp_L4, "Pie chart of Not Experienced users: Level 4")
draw_pie_chart(list_total_L4, "Pie chart of all users: Level 4")
####Level 5:
draw_pie_chart(list_exp_L5, "Pie chart of Experienced users: Level 5")
draw_pie_chart(list_nonexp_L5, "Pie chart of Not Experienced users: Level 5")
draw_pie_chart(list_total_L5, "Pie chart of all users: Level 5") 

####################################################################################################
####################################################################################################
setwd("~/Documents/GitHub/CS5099/PicrossGeneratorSolver/participants_instances")
getwd()
data2 <- read.table(file="only_timing.csv", sep = ",", as.is = TRUE, header = TRUE, stringsAsFactors=FALSE)
data2

library(psych)
pairs.panels(data2[5:9])  # select columns 5:9
data2[5:9] # all data are included
##################################################
#converting all time to numerical format:

col5 <- data2[,5]
col6 <- data2[,6]
col7 <- data2[,7]
col8 <- data2[,8]
col9 <- data2[,9]

col5
col6
col7
col8
col9

entire_list_seconds5 <- 60 * 60 * 24 * as.numeric(times(col5))
entire_list_seconds6 <- 60 * 60 * 24 * as.numeric(times(col6))
entire_list_seconds7 <- 60 * 60 * 24 * as.numeric(times(col7))
entire_list_seconds8 <- 60 * 60 * 24 * as.numeric(times(col8))
entire_list_seconds9 <- 60 * 60 * 24 * as.numeric(times(col9))

# all levels with all typers, which are converted to seconds:
entire_list_seconds5
entire_list_seconds6
entire_list_seconds7
entire_list_seconds8
entire_list_seconds9
mean1 <-mean(entire_list_seconds5)
mean2 <-mean(entire_list_seconds6)
mean3 <-mean(entire_list_seconds7)
mean4 <-mean(entire_list_seconds8)
mean5 <-mean(entire_list_seconds9)

seconds_to_period(mean1)
seconds_to_period(mean2)
seconds_to_period(mean3)
seconds_to_period(mean4)
seconds_to_period(mean5)


# collect in one data frame:
global_numeric_times <- data.frame(entire_list_seconds5, 
                                   entire_list_seconds6, 
                                   entire_list_seconds7,
                                   entire_list_seconds8, 
                                   entire_list_seconds9, 
                                   stringsAsFactors=FALSE)
global_numeric_times

#now we can use it here:
library(PerformanceAnalytics)
chart.Correlation(global_numeric_times)

# install.packages("corrplot")
library(corrplot)
x <- cor(global_numeric_times)
corrplot(x, type="upper", order="hclust")

#plotting:
boxplot(entire_list_seconds5, entire_list_seconds6, names=c("Level1", "Level2"))
plot(entire_list_seconds5, entire_list_seconds6)
plot(entire_list_seconds5)
abline(a=0, b=1)

#########################################################
#### T test on all axisting data:
#########################################################
# huge boxplot of all times: 
boxplot(entire_list_seconds5, entire_list_seconds6, entire_list_seconds7, entire_list_seconds8, entire_list_seconds9, 
        names = c("Level 1", "Level 2", "Level 3", "Level 4","Level 5"), 
        main = "All Levels for all participants", col="darkgreen", ylab="Seconds")
# clues: L1 L2 and L3 L4
boxplot(entire_list_seconds5, entire_list_seconds6)
boxplot(entire_list_seconds7, entire_list_seconds8)

t.test(entire_list_seconds5, entire_list_seconds6) 
t.test(entire_list_seconds7, entire_list_seconds8)

# different number of percentage
boxplot(entire_list_seconds5, entire_list_seconds7)
boxplot(entire_list_seconds6, entire_list_seconds8)

t.test(entire_list_seconds5, entire_list_seconds7)
t.test(entire_list_seconds6, entire_list_seconds8)

# different sizes:
boxplot(entire_list_seconds5, entire_list_seconds9)

t.test(entire_list_seconds5, entire_list_seconds9)

##################################################
#   T test only with successfully completed the game
##################################################
#getting all successfully completed data:
all_data_L1 <-get_filtered_lists_of_time_total(data, 5)
all_data_L2 <-get_filtered_lists_of_time_total(data, 7)
all_data_L3 <-get_filtered_lists_of_time_total(data, 9)
all_data_L4 <-get_filtered_lists_of_time_total(data, 11)
all_data_L5 <-get_filtered_lists_of_time_total(data, 13)
#output all:
all_data_L1[[1]]
all_data_L2[[1]]
all_data_L3[[1]]
all_data_L4[[1]]
all_data_L5[[1]]
#compare all means:
mean(all_data_L1[[1]])
mean(all_data_L2[[1]])
mean(all_data_L3[[1]])
mean(all_data_L4[[1]])
mean(all_data_L5[[1]])

mean1 <-mean(all_data_L1[[1]])
mean2 <-mean(all_data_L2[[1]])
mean3 <-mean(all_data_L3[[1]])
mean4 <-mean(all_data_L4[[1]])
mean5 <-mean(all_data_L5[[1]])

seconds_to_period(mean1)
seconds_to_period(mean2)
seconds_to_period(mean3)
seconds_to_period(mean4)
seconds_to_period(mean5)


boxplot(all_data_L1[[1]], all_data_L2[[1]], all_data_L3[[1]], all_data_L4[[1]], all_data_L5[[1]], names = c("Level 1", "Level 2", "Level 3", "Level 4","Level 5"), 
        main = "All Levels only successfully comeplted", col="darkgreen", ylab="Seconds")

# Correlation for the clues : need to comapre columns 5 and 7; and columns 9 and 11;
# in other words level 1 with 2; and level 3 with 4.
# Levels 1 and 2: everything the same, except the number of clues
# Levels 3 and 4: everything the same, except the number of clues

boxplot(all_data_L1[[1]], all_data_L2[[1]], names = c("Level 1", "Level 2"), 
        main = "Different number of clues", col=(c("gold","darkgreen")), ylab="Seconds")
boxplot(all_data_L3[[1]], all_data_L4[[1]], names = c("Level 3", "Level 4"), 
        main = "Different number of clues", col=(c("gold","darkgreen")), ylab="Seconds")

t.test(all_data_L1[[1]], all_data_L2[[1]])  # 7x7 70% less clues with 7x7 70% more clues
t.test(all_data_L3[[1]], all_data_L4[[1]])  # 7x7 50% less clues with 7x7 50% more clues

# Correlation for the percentage of the coloured cell - gives more information: 
# check column 5 with 9; check column 7 with 11.
# in other words level 1 with 3 and level 2 with 4.
# Levels 1 and 3: everything is the same except the percentage of coloured cells
# Levels 2 and 4: everything is the same except the percentage of coloured cells
boxplot(all_data_L1[[1]], all_data_L3[[1]], names = c("Level 1", "Level 3"), 
        main = "Different percentage of coloured cells", col=(c("gold","darkgreen")), ylab="Seconds")
boxplot(all_data_L2[[1]], all_data_L4[[1]], names = c("Level 2", "Level 4"), 
        main = "Different percentage of coloured cells", col=(c("gold","darkgreen")), ylab="Seconds")

t.test(all_data_L1[[1]], all_data_L3[[1]]) # both less clues and both 7x7 size, but different percentage 70% vs 50%
t.test(all_data_L2[[1]], all_data_L4[[1]]) # both more clues and both 7x7 size, but different percentage 70% vs 50% 

# Correlation for the size of entire field
# check column 5 with 13;
# in other words level 1 with 5:
# Levels 1 and 5 have everything the same except sizes: 7x7 vs 10x10
boxplot(all_data_L1[[1]], all_data_L5[[1]], names = c("Level 1", "Level 5"), 
        main = "Defferent sizes", col=(c("gold","darkgreen")), ylab="Seconds")
t.test(all_data_L1[[1]], all_data_L5[[1]])

#########################################################
#### T test on experienced users only
#########################################################
experienced_data <- subset(data, Experienced=="Yes")
experienced_data
experienced_data5 <- 60 * 60 * 24 * as.numeric(times(experienced_data[, 5]))
experienced_data6 <- 60 * 60 * 24 * as.numeric(times(experienced_data[, 7]))
experienced_data7 <- 60 * 60 * 24 * as.numeric(times(experienced_data[, 9]))
experienced_data8 <- 60 * 60 * 24 * as.numeric(times(experienced_data[, 11]))
experienced_data9 <- 60 * 60 * 24 * as.numeric(times(experienced_data[, 13]))
experienced_data5
experienced_data6
experienced_data7
experienced_data8
experienced_data9


mean(experienced_data5)
mean(experienced_data6)
mean(experienced_data7)
mean(experienced_data8)
mean(experienced_data9)
# the same:
mean1 <-mean(experienced_data5)
mean2 <-mean(experienced_data6)
mean3 <-mean(experienced_data7)
mean4 <-mean(experienced_data8)
mean5 <-mean(experienced_data9)


seconds_to_period(mean1)
seconds_to_period(mean2)
seconds_to_period(mean3)
seconds_to_period(mean4)
seconds_to_period(mean5)

boxplot(experienced_data5, experienced_data6, experienced_data7, experienced_data8, experienced_data9, names = c("Level 1", "Level 2", "Level 3", "Level 4", "Level 5"), 
        main = "All Levels for experienced participants", col="darkgreen", ylab="Seconds")

# different clues:
boxplot(experienced_data5, experienced_data6, names = c("Level 1", "Level 2"), main ="Different Clues for Experienced users" , col="darkgreen", ylab="Seconds")
boxplot(experienced_data7, experienced_data8, names = c("Level 3", "Level 4"), main ="Different Clues for Experienced users" , col="darkgreen", ylab="Seconds")

plot(experienced_data5, experienced_data6)
plot(experienced_data7, experienced_data8)

t.test(experienced_data5, experienced_data6)
t.test(experienced_data7, experienced_data8)

#different percentage:
boxplot(experienced_data5, experienced_data7, names = c("Level 1", "Level 3"), main ="Different Percentage for Experienced users" , col="darkgreen", ylab="Seconds")
boxplot(experienced_data6, experienced_data8, names = c("Level 2", "Level 4"), main ="Different Percentage for Experienced users" , col="darkgreen", ylab="Seconds")

plot(experienced_data5, experienced_data7)
plot(experienced_data6, experienced_data8)

t.test(experienced_data5, experienced_data7)
t.test(experienced_data6, experienced_data8)

# different sizes:
boxplot(experienced_data5, experienced_data9, names = c("Level 1", "Level 3"), main ="Different Sizes for Experienced users" , col="darkgreen", ylab="Seconds")
plot(experienced_data5, experienced_data9)
t.test(experienced_data5, experienced_data9)

i=1
len = length(switches$id)
list <- c()
tem<-switches$id
tem
while(i<len){
  if('45cc66cd921f617b7d6b929dab3c1a0d544ec6b8668813ee4458b845773fe4' == tem[i]){
    print(i)
  }
  # list <- c(list, )
  i<-i+1
}