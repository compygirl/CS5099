import pickle
import numpy as np
import matplotlib.pyplot as plt

def autolabel(rects, ax):
    """
    Attach a text label above each bar displaying its height
    Taken from matplotlib website.
    """
    for rect in rects:
        height = rect.get_height()
        ax.text(rect.get_x() + rect.get_width()/2., 1.05*height,
                format(height, '.2f'),
                ha='center', va='bottom',
                fontdict={"fontsize": 6})

def plot_graph(name, i, rf_array, rm_array):
    fig, ax = plt.subplots()
    rects1 = ax.bar(ind, rf_array, width, color='r')
    rects2 = ax.bar(ind + width, rm_array, width, color='b')
    if (i == 0):
        ax.set_ylabel('Node size')
        ext = "_node.png"
    if (i == 1):
        ax.set_ylabel('Time Taken')
        ext = "_time.png"
    if (i == 2):
        ax.set_ylabel('Arc revisions')
        ext = "_arc.png"
    ax.set_title(name.split("/")[1])
    ax.set_xticks(ind + width / 2)
    ax.set_xticklabels(('Degree', 'Cardinality', 'Min-Domain', 'Brelaz'))
    ax.legend((rects1[0], rects2[0]), ('FC', 'MAC'))
    autolabel(rects1, ax)
    autolabel(rects2, ax)
    plt.tight_layout()
    # plt.show()
    filename = name + ext
    plt.savefig(filename)

with open("results.dat", 'rb') as f:
    results = pickle.load(f)

ind = np.arange(4)
width = 0.35
median_time_fc = np.zeros([47,4])
median_arc_fc =  np.zeros([47,4])
median_node_fc = np.zeros([47,4])
median_fc = [median_node_fc, median_time_fc, median_arc_fc]

median_time_mac = np.zeros([47,4])
median_arc_mac =  np.zeros([47,4])
median_node_mac = np.zeros([47,4])
median_mac = [median_node_mac, median_time_mac, median_arc_mac]

count = -1
for key in results:
    count = count + 1
    for i in range(3):
        first = "sudoku_plots/" +  key.split("/")[1]
        filename = first
        r = results[key]
        rf = r["Forward_Checking_Solver"]
        rm = r["MAC_Solver"]
        rf_array = [rf["Degree"][i], rf["Cardinality"][i], rf["Min-Domain"][i], rf["Brelaz"][i]]
        rm_array = [rm["Degree"][i], rm["Cardinality"][i], rm["Min-Domain"][i], rm["Brelaz"][i]]
        median_fc[i][count]  = rf_array
        median_mac[i][count]  = rm_array
        plot_graph(filename, i, rf_array, rm_array)

median_node_fc = np.median(median_fc[0], 0)
median_time_fc = np.median(median_fc[1], 0)
median_arc_fc = np.median(median_fc[2], 0)
median_node_mac = np.median(median_mac[0], 0)
median_time_mac = np.median(median_mac[1], 0)
median_arc_mac = np.median(median_mac[2], 0)

plot_graph("sudoku_plots/median", 0, median_node_fc, median_node_mac)
plot_graph("sudoku_plots/median", 1, median_time_fc, median_time_mac)
plot_graph("sudoku_plots/median", 2, median_arc_fc, median_arc_mac)
