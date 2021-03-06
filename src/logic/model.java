package logic;

import ilog.cplex.*;
import ilog.cplex.IloCplex.UnknownObjectException;
import ilog.concert.*;

public class model {

	/**
	 * Here you can change the number of users.
	 */
	private static int n;
	private static IloCplex cplex;
	private static IloNumVar[][][] x;
	private static Node[] N;
	private static Truck[] K;

	private static IloNumVar[][] B;

	public static void main(String[] args) {

		// Uncomment if you want to automatically create Nodes
//		autoGenerateNodes(5);

		// Generate a predefined set of nodes.
		setDefaultNodes();

		// Print Node Positions for Excel.
//		System.out.println("Knoten\txPosition\tyPosition");
//		for (int i = 0; i <= 2*n+1; i++) {
//			System.out.println(i + "\t" + N[i].getxPosition() + "\t" + N[i].getyPosition());
//		}

		// Alle Trucks müssen die selben Container transportieren können.
		K = new Truck[3];
		K[0] = new Truck(new int[] { 1, 0, 0, 0 }, 500);
		K[1] = new Truck(new int[] { 2, 0, 0, 0 }, 1500);
		K[2] = new Truck(new int[] { 1, 0, 0, 0 }, 1000); // Mit einem Truck ohne Kapazität (capacity = 0) gibt es Bound
															// infeasibility column 'Q(i1;k2)'.

		// c enthält die Distanz zwischen allen Knoten
		double[][] c = new double[N.length][N.length];
		// t enthält die Fahrzeit zwischen allen Knoten.
		double[][] t = new double[N.length][N.length];

		double xDistance;
		double yDistance;

		for (int i = 0; i < N.length; i++) {
			for (int j = 0; j < N.length; j++) {
				if (i != j) {
					xDistance = Math.pow(N[i].getxPosition() - N[j].getxPosition(), 2);
					yDistance = Math.pow(N[i].getyPosition() - N[j].getyPosition(), 2);
					c[i][j] = Math.sqrt(xDistance + yDistance);

					// Die Fahrzeit zwischen i und j ist die Entfernung zwischen den Knoten * 60.
					t[i][j] = c[i][j] * 60;
				}
			}
		}

		try {
			cplex = new IloCplex();

			// Binary decision variable.
			x = new IloNumVar[N.length][N.length][K.length];
			for (int i = 0; i < N.length; i++) {
				for (int j = 0; j < N.length; j++) {
					if (i != j) {
						for (int k = 0; k < K.length; k++) {
							x[i][j][k] = cplex.boolVar("x(" + "i" + i + ";j" + j + ";k" + k + ")");
						}
					}
				}
			}

			// Zielfunktion
			IloLinearNumExpr obj = cplex.linearNumExpr();
			for (int i = 0; i < N.length; i++) {
				for (int j = 0; j < N.length; j++) {
					if (i != j) {
						for (int k = 0; k < K.length; k++) {
							obj.addTerm(c[i][j], x[i][j][k]);
						}
					}
				}
			}

			// Minimize the objective.
			cplex.addMinimize(obj);

			// Constraint 2: Visit every Pick up Location. (Serve every request exactly
			// once)
			// Funktioniert!
			// Cordeau geht von 1..n, Pesch von 1..2n
			// Von 1..n ist schneller aus von 1..2n.
			// Beides funktioniert.
			for (int i = 1; i <= n; i++) {
				IloLinearNumExpr expr = cplex.linearNumExpr();
				for (int k = 0; k < K.length; k++) {
					for (int j = 0; j < N.length; j++) {
						if (i != j) {
							expr.addTerm(1.0, x[i][j][k]);
						}
					}
				}
				cplex.addEq(expr, 1.0, "Constraint2");
			}

			// Constraint 3: visit pickup and dropdown depot by the same vehicle.
			for (int i = 1; i <= n; i++) {
				for (int k = 0; k < K.length; k++) {
					IloLinearNumExpr expr = cplex.linearNumExpr();
					for (int j = 0; j < N.length; j++) {
						if (i != j) {
							expr.addTerm(1.0, x[i][j][k]);
						}
					}
					for (int j = 0; j < N.length; j++) {
						if (i != j) {
							if (n + i != j)
								expr.addTerm(-1.0, x[n + i][j][k]);
						}
					}
					cplex.addEq(expr, 0.0, "Constraint3");
				}
			}

			// Constraint 4: Start route at the origin depot.
			for (int k = 0; k < K.length; k++) {
				IloLinearNumExpr expr = cplex.linearNumExpr();
				for (int j = 0; j < N.length; j++) {
					if (j != 0) {
						expr.addTerm(1.0, x[0][j][k]);
					}
				}
				cplex.addEq(expr, 1.0, "Constraint4");
			}

			// Constraint 5: Flow constraint: Every Node from P union D (1..2*n)
			// must have the same amount of edges going and edges going out.
			// The Nodes 0 and 2n+1 (7) are not covered by this constraint, because
			// the route should start/end there.
			for (int i = 1; i <= 2 * n; i++) {
				for (int k = 0; k < K.length; k++) {
					IloLinearNumExpr expr = cplex.linearNumExpr();
					for (int j = 0; j < N.length; j++) {
						if (i != j) {
							expr.addTerm(1.0, x[j][i][k]);
						}
					}
					for (int j = 0; j < N.length; j++) {
						if (i != j) {
							expr.addTerm(-1.0, x[i][j][k]);
						}
					}
					cplex.addEq(expr, 0.0, "Constraint5");
				}
			}

			// Constraint 6: End Route at destination depot.
			for (int k = 0; k < K.length; k++) {
				IloLinearNumExpr expr = cplex.linearNumExpr();
				for (int i = 0; i < N.length; i++) {
					if (i != 2 * n + 1) {
						expr.addTerm(1.0, x[i][2 * n + 1][k]);
					}
				}
				cplex.addEq(expr, 1.0, "Constraint6");
			}

			// Kontinuirliche Variable B_ik für die Zeit, an der Truck seinen
			// Service an Knoten i beginnt.
			B = new IloNumVar[N.length][K.length];
			for (int i = 0; i < N.length; i++) {
				for (int k = 0; k < K.length; k++) {
					B[i][k] = cplex.numVar(0, 1440, "ServiceTimeB(i" + i + ";k" + k + ")");
				}
			}

			// Constraint 7: Der Service an Knoten j kann erst beginnen,
			// nachdem der Service an Knoten i abgeschlossen wurde und der
			// LKW von i nach j gefahren ist.
			double M;
			for (int i = 0; i < N.length; i++) {
				for (int j = 0; j < N.length; j++) {
					if (i != j) {
						for (int k = 0; k < K.length; k++) {
							IloLinearNumExpr expr = cplex.linearNumExpr();
							M = Math.max(0, N[i].getEndServiceTime() + N[i].getServiceDuration() + t[i][j]
									- N[j].getBeginServiceTime());
							expr.addTerm(1.0, B[i][k]);
							expr.setConstant(N[i].getServiceDuration() + t[i][j] - M);
							expr.addTerm(M, x[i][j][k]);
							cplex.addGe(B[j][k], expr, "Constraint7");
						}
					}
				}
			}

			// Definition Variable Q_ik: Load of vehicle k after visiting node i.
			IloNumVar[][][] Q = new IloNumVar[N.length][4][K.length];
			for (int i = 0; i < N.length; i++) {
				for (int r = 0; r <= 3; r++) {
					for (int k = 0; k < K.length; k++) {
						Q[i][r][k] = cplex.numVar(0, K[k].getCapacity()[r], "Q(i" + i + ";r" + r + ";k" + k + ")");
					}
				}
			}

			// Constraint 8: Die geladenen Ressourcen auf LKW k müssen bei Knoten i
			// plus dem Load von Knoten i kleiner/gleich den geladenen Ressourcen
			// bei Knoten j sein.
			// Version von Cordeau: Ist schneller als die Version von Pesch.
			// Liefert das selbe Ergebnis wie Pesch.
//			double W;
//			for (int i = 0; i < N.length; i++) {
//				for (int j = 0; j < N.length; j++) {
//					if (i != j) {
//						for (int k = 0; k < K.length; k++) {
//							for (int r = 0; r <= 3; r++) {
//								W = Math.min(K[k].getCapacity()[r], K[k].getCapacity()[r] + N[i].getLoad()[r]);
//								IloLinearNumExpr expr = cplex.linearNumExpr();
//								expr.addTerm(1.0, Q[i][r][k]);
////								expr.setConstant(N[j].getLoad()[r] - W);
//								expr.setConstant(N[j].getLoad()[r] - 100);
////								expr.addTerm(W, x[i][j][k]);
//								expr.addTerm(100, x[i][j][k]);
//								cplex.addGe(Q[j][r][k], expr, "Constraint8(i" + i + ";j" + j + ";k" + k + ";r" + r + ")");
//							}
//						}
//					}
//				}
//			}

			// Version von Pesch: Ist langsamer als die Version von Cordeau.
			// Liefert das selbe Ergebnis wie Pesch.
			for (int k = 0; k < K.length; k++) {
			//Modell geändert: Im Original wird anstatt N=PuD N=PuDu{0, 2n+1} genommen.
			//Start und Zieldepot sind in dieser Variante inbegriffen.
				for (int i = 0; i < N.length; i++) {
				// Hier das selbe wie beim vorherigen Kommentar. Für Start- und Zielknoten 
				// gilt diese Bedingung auch.
					for (int j = 0; j < N.length; j++) {
						if (i != j) {
							for (int r = 0; r <= 3; r++) {
//								if (N[i].getLoad()[r] != 0) {
//									if (N[j].getLoad()[r] != 0) {
//								if (K[k].getCapacity()[r] != 0 && N[i].getLoad()[r] != 0 && N[j].getLoad()[r] != 0) {
									IloLinearNumExpr expr1 = cplex.linearNumExpr();
									expr1.addTerm(1.0, Q[i][r][k]);
									// Hier ist die big M notation abggeändert.
									// Groß M soll eigentlich die Kapazität des LKW sein, ist jetzt aber 100
//									expr1.setConstant(N[j].getLoad()[r] + K[k].getCapacity()[r]);
									expr1.setConstant(N[j].getLoad()[r] + 100);
//									expr1.addTerm(-K[k].getCapacity()[r], x[i][j][k]);
									expr1.addTerm(-100, x[i][j][k]);
									cplex.addLe(Q[j][r][k], expr1, "Contraint10a(k" + k + ";i" + i + ";j" + j + ";r" + r + ")");
									
									IloLinearNumExpr expr2 = cplex.linearNumExpr();
									expr2.addTerm(1.0, Q[i][r][k]);
//									expr2.setConstant(N[j].getLoad()[r] - K[k].getCapacity()[r]);
									expr2.setConstant(N[j].getLoad()[r] - 100);
//									expr2.addTerm(K[k].getCapacity()[r], x[i][j][k]);
									expr2.addTerm(100, x[i][j][k]);
									cplex.addGe(Q[j][r][k], expr2, "Constraint10b(k" + k + ";i" + i + ";j" + j + ";r" + r + ")");
//								}
//									}
//								}
							}
						}
					}
				}
			}

			// Maximum ride time of a user: For example 180 Minutes.
			double lMaxRideTime = 360;

			// Definition L_i^k: The ride time of user i on vehicle k.
			IloNumVar[][] L = new IloNumVar[N.length][K.length];
			for (int i = 1; i <= n; i++) {
				for (int k = 0; k < K.length; k++) {
					L[i][k] = cplex.numVar(0, lMaxRideTime, "L(i" + i + ";k" + k + ")");
				}
			}

			// Constraint 9 Cordeau: Set the ride time of each user.
			// Ride time of user i in vehicle k (L_i^k)
			// ist gleich Ride Time of user i + n minus (Ride time in
			// i plus service time in i).
			for (int i = 1; i <= n; i++) {
				for (int k = 0; k < K.length; k++) {
					IloLinearNumExpr expr = cplex.linearNumExpr();
					expr.addTerm(1.0, B[n + i][k]);
					expr.addTerm(-1.0, B[i][k]);
					expr.setConstant(-N[i].getServiceDuration());
					cplex.addEq(L[i][k], expr, "Constraint9");
				}
			}

			// Constraint 10 Cordeau: Dauer einer Tour darf die maximale
			// Tourzeit eines LKWs nicht überschreiten.
			for (int k = 0; k < K.length; k++) {
				IloLinearNumExpr expr = cplex.linearNumExpr();
				expr.addTerm(1.0, B[2 * n + 1][k]);
				expr.addTerm(-1.0, B[0][k]);
				cplex.addLe(expr, K[k].getMaxTourTime(), "Constraint10");
			}

			// Constraint 11 Cordeau: Knoten müssen innerhalb ihrer Servicezeit besucht werden.
			// Constraint 20 Pesch
			for (int i = 0; i < N.length; i++) {
				for (int k = 0; k < K.length; k++) {
					cplex.addLe(N[i].getBeginServiceTime(), B[i][k], "Constraint11_1");
					cplex.addLe(B[i][k], N[i].getEndServiceTime(), "Constraint11_2");
				}
			}

			// Constraint 12: Ride time jedes users muss größer als
			// die Fahrzeit von Knoten i nach Knoten j sein und kleiner als
			// der maximal erlaubte Fahrzeit.
			for (int i = 1; i <= n; i++) {
				for (int k = 0; k < K.length; k++) {
					cplex.addLe(t[i][n + i], L[i][k], "Constraint12_1");
					cplex.addLe(L[i][k], lMaxRideTime, "Constraint12_2");
				}
			}

			// Constraint 13 Cordeau: impose capacity constraint
			// Constraint 11 Pesch
			// Cordeau
//			for (int i = 0; i < N.length; i++) {
//				for (int k = 0; k < K.length; k++) {
//					for (int r = 0; r <= 3; r++) {
//						cplex.addLe(Math.max(0, N[i].getLoad()[r]), Q[i][r][k], "Constraint13_1");
//						cplex.addLe(Q[i][r][k],
//								Math.min(K[k].getCapacity()[r], K[k].getCapacity()[r] + N[i].getLoad()[r]),
//								"Constraint13_2");
//					}
//				}
//			}

			// Pesch
			for (int k = 0; k < K.length; k++) {
				for (int i = 0; i < N.length; i++) {
					for (int r = 0; r <= 3; r++) {
						cplex.addLe(0.0, Q[i][r][k], "Constraint11_1");
						cplex.addLe(Q[i][r][k], K[k].getCapacity()[r], "Constraint11_2");
					}
				}
			}

			// Constraint 12 Pesch: Leere und volle 30 Fuß Container dürfen die Kapazität des LKWs
			// nicht überschreiten. Bsp. Ein LKW kann nicht 2 volle 30 Fuß Container und 2 
			// leere 30 Fuß Container laden, da er nur Platz für insgesamt 2 Container hat.
			for (int k = 0; k < K.length; k++) {
				for (int i = 1; i <= 2 * n; i++) {
					IloLinearNumExpr expr = cplex.linearNumExpr();
					expr.addTerm(1.0, Q[i][0][k]);
					expr.addTerm(1.0, Q[i][1][k]);
					cplex.addLe(expr, K[k].getCapacity()[0], "Constraint12");
				}
			}

			// Constraint 13 Pesch: Leere und volle 60 Fuß Container zusammen dürfen die Kapazität
			// des LKWs nicht übeschreiten. Bsp.: Es kann nicht ein voller und ein leerer 60"
			// Container gleichzeitig geladen sein.
			for (int k = 0; k < K.length; k++) {
				for (int i = 0; i <= 2 * n; i++) {
					IloLinearNumExpr expr = cplex.linearNumExpr();
					expr.addTerm(1.0, Q[i][2][k]);
					expr.addTerm(1.0, Q[i][3][k]);
					cplex.addLe(expr, K[k].getCapacity()[2], "Constraint13");
				}
			}

			// Constraint 14 Pesch: Start with empty
			// Constraint 14 und 15 haben den Algorithmus doppelt so schnell gemacht.
			// Beide Constraints sind aber nicht notwendig.
			// Pesch
			for (int k = 0; k < K.length; k++) {
				for (int r = 0; r <= 3; r++) {
					cplex.addEq(Q[0][r][k], 0.0, "Constraint14");
				}
			}

			// Constraint 15 Pesch: Route beenden ohne container.
			// Constraint 14 und 15 haben den Algorithmus doppelt so schnell gemacht.
			// Beide Constraints sind aber nicht notwendig.
			// Pesch
			for (int k = 0; k < K.length; k++) {
				for (int r = 0; r <= 3; r++) {
					cplex.addEq(Q[N.length - 1][r][k], 0.0, "Constraint15");
				}
			}

			// Exportieren des Modells
			cplex.exportModel("Cordeau.lp");

			solveModel();

			cplex.end();

		} catch (IloException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Method to look up the route of a vehicle.
	 * @param row
	 * @param truck
	 * @return The next node on the route.
	 */
	public static int getNextNode(int row, int truck) {
		for (int i = 0; i <= 2 * n + 1; i++) {
			try {
				if (i != row) {
					if (Math.round(cplex.getValue(x[row][i][truck])) == 1) {
						return i;
					}
				}
			} catch (UnknownObjectException e) {
				e.printStackTrace();
			} catch (IloException e) {
				e.printStackTrace();
			}
		}
		return 0;
	}

	/**
	 * Automatically generates random Nodes.
	 * 
	 * @param numberOfNodes
	 */
	public static void autoGenerateNodes(int numberOfNodes) {
		// Auto generate Nodes:
		n = numberOfNodes;

		N = new Node[2 * n + 2];
		// Start Node is the origin depot.
		N[0] = new Node(Math.random() * 4 + 1, Math.random() * 4 + 1, 0, 1440, new int[] { 0, 0, 0, 0 }, 0);

		// Pick up nodes 1..n
		for (int i = 1; i <= n; i++) {
			N[i] = new Node(Math.random() * 4 + 1, Math.random() * 4 + 1, 0, 1440, new int[] { 1, 0, 0, 0 }, 30);
		}

		// Drop down nodes n+1..2n
		for (int i = n + 1; i <= 2 * n; i++) {
			N[i] = new Node(Math.random() * 4 + 1, Math.random() * 4 + 1, 0, 1440, new int[] { -1, 0, 0, 0 }, 30);
		}

		// Destination Node 2n+1 is the last node.
		N[2 * n + 1] = new Node(Math.random() * 4 + 1, Math.random() * 4 + 1, 0, 1440, new int[] { 0, 0, 0, 0 }, 0);
	}

	/**
	 * Generate a default set of Nodes.</br>
	 * 3 pickup locations, 3 dropdown locations, the start node and a end node will
	 * be created.
	 */
	public static void setDefaultNodes() {
		n = 5;

		N = new Node[12];
		// The start node.
		N[0] = new Node(1, 2, 0, 1440, new int[] { 0, 0, 0, 0 }, 0);

		// The pick up nodes.
		N[1] = new Node(1, 1, 0, 1440, new int[] { 1, 0, 0, 0 }, 30);
		N[2] = new Node(1, 4, 0, 1440, new int[] { 2, 0, 0, 0 }, 30);
		N[3] = new Node(4, 3, 0, 1440, new int[] { 1, 0, 0, 0 }, 30);
		N[4] = new Node(2, 2, 0, 1440, new int[] { 2, 0, 0, 0 }, 30);
		N[5] = new Node(2, 4, 0, 1440, new int[] { 1, 0, 0, 0 }, 30);

		// The drop down nodes.
		N[6] = new Node(4, 1, 0, 1440, new int[] { -1, 0, 0, 0 }, 30);
		N[7] = new Node(4, 4, 0, 1440, new int[] { -2, 0, 0, 0 }, 30);
		N[8] = new Node(1, 3, 0, 1440, new int[] { -1, 0, 0, 0 }, 30);
		N[9] = new Node(3, 4, 0, 1440, new int[] { -2, 0, 0, 0 }, 30);
		N[10] = new Node(3, 1, 0, 1440, new int[] { -1, 0, 0, 0 }, 30);

		// The end depot.
		N[11] = new Node(3, 2, 0, 1440, new int[] { 0, 0, 0, 0 }, 0);
	}

	private static void solveModel() {
		try {
			// Solve the model
			if (cplex.solve()) {
				// Print the result
				System.out.println("Solution status: " + cplex.getStatus());
				System.out.println("--------------------------------------------");
				System.out.println();
				System.out.println("Solution found:");
				System.out.println(" Objective value = " + cplex.getObjValue());
				System.out.println();

				for (int k = 0; k < K.length; k++) {
					System.out.println("Solution for Truck " + k);
					for (int i = 0; i <= 2 * n + 1; i++) {
						System.out.print("\t" + i);
					}
					System.out.println();
					for (int i = 0; i < N.length; i++) {
						System.out.print(i + "\t");
						for (int j = 0; j < N.length; j++) {
							if (i != j) {
								// Möglicherweise müssen Werte gerundet werden.
								if (cplex.getValue(x[i][j][k]) == 0) {
									System.out.print("-\t");
								} else {
									System.out.print(Math.round(cplex.getValue(x[i][j][k])) + "\t");
								}
							} else {
								System.out.print("\\\t");
							}
						}
						System.out.println();
					}

					System.out.println("Route duration for Truck " + k + ": "
							+ Math.round(cplex.getValue(B[2 * n + 1][k])) + " minutes.");

					int nextNode = getNextNode(0, k);
					System.out.print("Route: 0 -> ");
					while (nextNode != 0) {
						if (nextNode != 2 * n + 1) {
							System.out.print(nextNode + " -> ");
						} else {
							System.out.print(nextNode);
						}
						nextNode = getNextNode(nextNode, k);
					}
					System.out.println();
					nextNode = getNextNode(0, k);
					System.out.println("Knoten\txPosition\tyPosition");
					System.out.println("0\t" + N[0].getxPosition() + "\t" + N[0].getyPosition());
					while (nextNode != 0) {
						System.out.println(
								nextNode + "\t" + N[nextNode].getxPosition() + "\t" + N[nextNode].getyPosition());
						nextNode = getNextNode(nextNode, k);
					}
					System.out.println();
				}
			} else {
				System.out.println("No solution exists");
			}
		} catch (IloException e) {
			e.printStackTrace();
		}
	}

}
