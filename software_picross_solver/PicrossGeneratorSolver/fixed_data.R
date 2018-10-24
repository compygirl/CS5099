#################################################################
### CS5099: MSc Disseratation in CS
### 
### ID: 090017799
#################################################################
#--------------------------------------------------------------------
# Get into the working directory, where the program writes results of 
# run to the CSV file:
#--------------------------------------------------------------------
setwd("~/Documents/GitHub/CS5099/PicrossGeneratorSolver/stat_data")

getwd()

#--------------------------------------------------------------------
# reading CSV file to the "data" variable
#--------------------------------------------------------------------
set_data<-read.csv("participants.csv", stringsAsFactors=FALSE)
set_data

# attach(set_data)

#--------------------------------------------------------------------
# Solvable instances - those which initially has one solution
#
# This function returns the data, which  stores solvable instances 
# of one run. 
# data stores filenames, solver_total_time and savile_row_total_time
#
#--------------------------------------------------------------------

get_time_for_solvable_instances<-function(all_data, column_uuid_string, uniq_id){
  i <- 1
  total_num_rows <- length(all_data$UUID)
  filenames <- c()
  solver_total_time <- c()
  savile_row_total_time <- c()
  
  while(i<=total_num_rows){
    if(all_data[i, column_uuid_string] == uniq_id & all_data$STEPS[i] == 0 & all_data$SolverSolutionsFound[i] == 1){
      filenames <- c(filenames, all_data$Filenames[i])
      solver_total_time <- c(solver_total_time, all_data$SolverTotalTime[i])
      savile_row_total_time <- c(savile_row_total_time, all_data$SavileRowTotalTime[i])
    }
    i <- i + 1
  }
  data <- data.frame(filenames, solver_total_time, savile_row_total_time, stringsAsFactors=FALSE)
  return(data)
}

get_all_filename_indexes<-function(checked_data){
  total_num<-length(checked_data$filenames)
  array_index <- c()
  for(i in 1:total_num){
    index<-unique(na.omit(as.numeric(unlist(strsplit(unlist(checked_data$filenames[i]), "[^0-9]+")))))
    array_index <- c(array_index, index)
  }
  return(array_index)
}

drawing_bar_chart_solver_total_time<-function(checked_data, index){
  filename_indexes<-get_all_filename_indexes(checked_data)
  main_stirng<-paste("Solver Total Time", index, sep="")
  barplot(checked_data$solver_total_time, col = "darkgreen", main=main_stirng, xlab="Files", ylab="Time", names.arg = filename_indexes)
}

drawing_bar_chart_savile_row_total_time<-function(checked_data, index){
  filename_indexes<-get_all_filename_indexes(checked_data)
  main_stirng<-paste("Savile Row Total Time", index, sep="")
  barplot(checked_data$savile_row_total_time, col = "darkgreen", main=main_stirng, xlab="Files", ylab="Time", names.arg = filename_indexes)
}

iterate_trough_all_possible_runs<-function(all_file){
  total_amount <- length(unique(all_file[, "UUID"]))
  # now iterate and draw all
  for(i in 1:total_amount){
    graph_data<-get_time_for_solvable_instances(all_file, "UUID", unique(all_file[, "UUID"])[i])
    drawing_bar_chart_solver_total_time(graph_data, i)
    drawing_bar_chart_savile_row_total_time(graph_data, i)
  }
}
iterate_trough_all_possible_runs(set_data)

# data$filenames <-factor(data$filenames, levels = data[["filenames"]])
# plot_ly(data, x = ~data$filenames, y = ~data$solver_total_time, type = 'bar', name = "SOLVER") %>%
#   layout(yaxis = list(title = 'Time'))

