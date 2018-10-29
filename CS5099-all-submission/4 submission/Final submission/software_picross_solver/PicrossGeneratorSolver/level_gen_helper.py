def find_many_clues(list_of_clues):
    # -------------------------------------------------------------------------
    # returns the amount of clues for the instance with the maximum amount,
    # as well as it returns the the index of the instance in the list of
    # instances, which were generated.
    # -------------------------------------------------------------------------
    max = 0
    max_index = 0
    for i in range(0, len(list_of_clues)):
        if max < list_of_clues[i]:
            max = list_of_clues[i]
            max_index = i
    return max, max_index


def find_less_clues(list_of_clues):
    # -------------------------------------------------------------------------
    # opposite to the find_many_clues function
    # -------------------------------------------------------------------------
    max_value, max_index = find_many_clues(list_of_clues)
    min = list_of_clues[max_index]
    min_index = max_index
    happened = False
    for i in range(0, len(list_of_clues)):
        if list_of_clues[i] > 0 and list_of_clues[i] < min:
            min = list_of_clues[i]
            min_index = i
            happened = True
    if not happened:
        print "This case has all the cases are the same!"
    return min, min_index


def count_num_coloured_cells(propotion, rows, columns):
    # -------------------------------------------------------------------------
    # Function counts how many cells should be coloured since the user provides
    # it in percentage.
    # -------------------------------------------------------------------------
    total_amount_cells = rows * columns
    amount_coloured_cells = round((total_amount_cells / 100.0) * propotion)
    return amount_coloured_cells
