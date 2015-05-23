package it.unimi.dsi.sux4j.mph;


import it.unimi.dsi.Util;
import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntHeapIndirectPriorityQueue;
import it.unimi.dsi.fastutil.longs.LongBigList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

public class Modulo3System {
	private final static boolean DEBUG = false;
	
	/** A modulo-3 equation. */
	public static class Modulo3Equation {
		/** The constant term. */
		protected int c;
		private final int numVars;
		protected final LongArrayBitVector bv;
		protected final long[] bits;
		private final LongBigList list;
		protected int coeff;
		private int firstVar;
		private int firstCoeff;
		private int wordIndex;
		private long word;

		/** Creates a new equation.
		 * 
		 * @param c the constant term.
		 */
		public Modulo3Equation( final int c, final int numVars ){
			this.c = c;
			this.bv = LongArrayBitVector.ofLength( numVars * 2 + 1 ); // + 1 for the sentinel.
			bv.set( ( this.numVars = numVars ) * 2 ); // The sentinel
			this.bits = bv.bits();
			this.list = bv.asLongBigList( 2 );
			this.firstVar = Integer.MAX_VALUE;
		}
		
		protected Modulo3Equation( final Modulo3Equation equation ){
			this.c = equation.c;
			this.bv = equation.bv.copy();
			this.bits = bv.bits();
			this.numVars = equation.numVars;
			this.list = this.bv.asLongBigList( 2 );
			this.firstVar = equation.firstVar;
			this.firstCoeff = equation.firstCoeff;
		}
		
		public Modulo3Equation add( final int index, final int value ) {
			list.set( index, value );
			if ( index < firstVar ) {
				firstVar = index;
				firstCoeff = value;
			}
			return this;
		}
		
		public Modulo3Equation add( final int index ) {
			return add( index, 1 );
		}
		
		public int[] variables() {
			IntArrayList variables = new IntArrayList();
			for( int var = firstVar(); var != Integer.MAX_VALUE; var = nextVar() ) variables.add( var );
			return variables.toIntArray();
		}

		public int[] coefficients() {
			IntArrayList coefficients = new IntArrayList();
			for( int var = firstVar(); var != Integer.MAX_VALUE; var = nextVar() ) coefficients.add( coeff );
			return coefficients.toIntArray();
		}
		
		public Modulo3Equation eliminate( final Modulo3Equation equation, final int var ) {
			final int mul = this.list.get( var ) == equation.list.get( var ) ? 1 : 2;
			sub( equation, mul );
			return this;

			//System.err.println( this + " - " + mul + " * " + equation + "  =>  " + result );
			//assert result.sub( equation, 3 - mul ).equals( this ) : result.sub( equation, 3 - mul ) + " != " + this;
		}

		protected final static long addMod3( final long x, final long y ) {
	        // mask the high bit of each pair set iff the result of the
	        // sum in that position is >= 3
	        // check if x is 2 and y is nonzero ...
	        long mask = x & (y | (y << 1));
	        // ... or x is 1 and y is 2
	        mask |= (x << 1) & y;
	        // clear the low bits
	        mask &= 0x5555555555555555L << 1;
	        // and turn 2s into 3s
	        mask |= mask >> 1;
	        return (x + y) - mask;
	    }

		protected final static long subMod3( final long x, long y ) {
	        // Change of sign
	        y = ( y & 0x5555555555555555L ) << 1 | ( y & 0xAAAAAAAAAAAAAAAAL ) >>> 1;
	        // mask the high bit of each pair set iff the result of the
	        // sum in that position is >= 3
	        // check if x is 2 and y is nonzero ...
			long mask = x & ( y | (y << 1) );
	        // ... or x is 1 and y is 2
	        mask |= (x << 1) & y;
	        // clear the low bits
	        mask &= 0x5555555555555555L << 1;
	        // and turn 2s into 3s
	        mask |= mask >> 1;
	        return (x + y) - mask;
	    }

		private void setFirstVar() {
			int i = -1;
			while( bits[ ++i ] == 0 );
			final int lsb = Long.numberOfTrailingZeros( bits[ i ] ) / 2;
			final int candidateFirstVar = lsb + 32 * i;
			if ( candidateFirstVar >= numVars ) {
				// Sentinel
				firstVar = Integer.MAX_VALUE;
				return;
			}
			firstVar = candidateFirstVar;
			firstCoeff = (int)( bits[ i ] >> lsb * 2 & 3 );
			return;
		}

		private final void addMod3( final LongArrayBitVector x, final LongArrayBitVector y ) {
			final long[] bx = x.bits(), by = y.bits();
			for( int i = (int)( ( x.length() + 63 ) / 64 ); i-- != 0; ) bx[ i ] = addMod3( bx[ i ], by[ i ] );
		}
		
		private final void subMod3( final LongArrayBitVector x, final LongArrayBitVector y ) {
			final long[] bx = x.bits(), by = y.bits();
			for( int i = (int)( ( x.length() + 63 ) / 64 ); i-- != 0; ) bx[ i ] = subMod3( bx[ i ], by[ i ] );
		}
		
		/** Subtract from this equation another equation multiplied by a provided constant.
		 * 
		 * @param equation the subtrahend.
		 * @param mul a multiplier that will be applied to the subtrahend.
		 */
		
		public void sub( final Modulo3Equation equation, final int mul ) {
			// Explicit multiplication tables to avoid modulo operators.
			if ( mul == 1 ) {
				c = ( c + 2 * equation.c ) % 3;
				subMod3( bv, equation.bv );
			}
			else {
				c = ( c + equation.c ) % 3;
				addMod3( bv, equation.bv );
			}
			bv.set( numVars * 2 ); // Be sure that the sentinel is still there
			setFirstVar();
		}
		
		public int firstVar() {
			if ( firstVar == Integer.MAX_VALUE ) return Integer.MAX_VALUE;
			coeff = firstCoeff;
			wordIndex = firstVar / 32;
			word = bits[ wordIndex ];
			assert firstVar == wordIndex * 32 + Long.numberOfTrailingZeros( word ) / 2;
			word &= word - 1; 
			return firstVar;
		}

		public int nextVar() {
			while( word == 0 ) word = bits[ ++wordIndex ];
			
			final int lsb = Long.numberOfTrailingZeros( word );
			final int nextVar = wordIndex * 32 + lsb / 2;
			if ( nextVar >= numVars ) return Integer.MAX_VALUE;
			
			coeff = (int)( word >> ( lsb & ~1 ) & 3 );
			word &= word - 1;
			
			return nextVar;
		}
		
		public boolean isUnsolvable() {
			return firstVar == Integer.MAX_VALUE && c != 0;
		}

		public boolean isIdentity() {
			return firstVar == Integer.MAX_VALUE && c == 0;
		}
		
		@Override
		public boolean equals( Object o ) {
			if ( ! ( o instanceof Modulo3Equation ) ) return false;
			final Modulo3Equation equation = (Modulo3Equation)o;
			return c == equation.c && bv.equals( equation.bv );
		}

		public void normalized( final long[] result ) {
			final long[] bits = this.bits;
			// Drop coefficients
			for( int i = bits.length; i-- != 0; ) result[ i ] = ( bits[ i ] & 0x5555555555555555L ) | ( bits[ i ] & 0xAAAAAAAAAAAAAAAAL ) >>> 1;
		}
		
		public String toString() {
			StringBuilder b = new StringBuilder();
			boolean someNonZero = false;
			for( int i = 0; i < list.size(); i++ ) {
				if ( list.getLong( i ) != 0 ) {
					if ( someNonZero ) b.append( " + " );
					someNonZero = true;
					b.append( list.getLong( i ) == 1 ? "x" : "2x" ).append( '_' ).append( i );
				}
	 		}
			if ( ! someNonZero ) b.append( '0' );
			return b.append( " = " ).append( c ).toString();
		}
		
		public Modulo3Equation copy() {
			return new Modulo3Equation( this );
		}
	}

	private final ArrayList<Modulo3Equation> equations;
	private final int numVars;

	public Modulo3System( final int numVar ) {
		equations = new ArrayList<Modulo3Equation>();
		this.numVars = numVar;
	}

	protected Modulo3System( ArrayList<Modulo3Equation> system, final int numVar ) {
		this.equations = system;
		this.numVars = numVar;
	}
	
	public Modulo3System copy() {
		ArrayList<Modulo3Equation> list = new ArrayList<Modulo3System.Modulo3Equation>( equations.size() );
		for( Modulo3Equation equation: equations ) list.add( equation.copy() );
		return new Modulo3System( list, numVars );
	}

	@Override
	public String toString() {
		StringBuilder b = new StringBuilder();
		for ( int i = 0; i < equations.size(); i++ ) b.append( equations.get( i ) ).append( '\n' );
		return b.toString();
	}

	public void add( Modulo3Equation equation ) {
		if ( equation.numVars != numVars ) throw new IllegalArgumentException( "The number of variables in the equation (" + equation.list.size() + ") does not match the number of variables of the system (" + numVars + ")" );
		equations.add( equation );
	}

	public boolean echelonForm() {
		main: for ( int i = 0; i < equations.size() - 1; i++ ) {
			assert equations.get( i ).firstVar != Integer.MAX_VALUE;
			
			for ( int j = i + 1; j < equations.size(); j++ ) {
				// Note that because of exchanges we cannot extract the first assignment
				Modulo3Equation eqJ = equations.get( j );
				Modulo3Equation eqI = equations.get( i );

				assert eqI.firstVar != Integer.MAX_VALUE;
				assert eqJ.firstVar != Integer.MAX_VALUE;

				final int firstVar = eqI.firstVar;

				if( firstVar == eqJ.firstVar ) {
					eqI.eliminate( eqJ, firstVar );
					if ( eqI.isUnsolvable() ) return false;
					if ( eqI.isIdentity() ) continue main;
					assert firstVar != eqI.firstVar : firstVar + " = " + eqI.firstVar;
				}

				if ( eqI.firstVar > eqJ.firstVar ) Collections.swap( equations, i, j );
			}
		}
		return true;
	}

	public boolean structuredGaussianElimination( final int[] solution ) {
		assert solution.length == numVars;
		LongArrayBitVector solutions = LongArrayBitVector.ofLength( numVars * 2 );
		if ( ! structuredGaussianElimination( solutions ) ) return false;
		final LongBigList list = solutions.asLongBigList( 2 );
		for( int i = solution.length; i-- != 0; ) solution[ i ] = (int)list.getLong( i );
		return true;
	}

	public boolean structuredGaussianElimination( final LongArrayBitVector solution ) {
		assert solution.length() == numVars * 2;

		if ( DEBUG ) {
			System.err.println();
			System.err.println( "====================" );
			System.err.println();
			System.err.println( this );
		}
			
		final int numEquations = equations.size();
		if ( numEquations == 0 ) return true;
		/* The weight of each variable, that is, the opposite of number of equations still 
		 * in the queue in which the variable appears. We use negative weights to have
		 * variables of weight of maximum modulus at the top of the queue. */
		final int weight[] = new int[ numVars ];
		// For each variable, the equations still in the queue containing it.
		final IntArrayList[] varEquation = new IntArrayList[ numVars ];
		// The priority of each equation still in the queue (the number of light variables).
		final int[] priority = new int[ numEquations ];

		for( int i = 0; i < numEquations; i++ ) {
			final Modulo3Equation equation = equations.get( i );
			// Initially, all variables are light.
			for( int var = equation.firstVar(); var != Integer.MAX_VALUE; var = equation.nextVar() ) {  
				if ( varEquation[ var ] == null ) varEquation[ var ] = new IntArrayList( 8 );
				weight[ var ]--;
				varEquation[ var ].add( i );
				priority[ i ]++;
			}
		}

		final boolean[] isHeavy = new boolean[ numVars ];
		IntHeapIndirectPriorityQueue variableQueue = new IntHeapIndirectPriorityQueue( weight, Util.identity( numVars ) );
		
		// The equations that are neither dense, nor solved.
		IntHeapIndirectPriorityQueue equationQueue = new IntHeapIndirectPriorityQueue( priority, Util.identity( numEquations ) );
		
		ArrayList<Modulo3Equation> dense = new ArrayList<Modulo3Equation>();
		ArrayList<Modulo3Equation> solved = new ArrayList<Modulo3Equation>();
		IntArrayList pivots = new IntArrayList();

		final long[] normalized = new long[ equations.get( 0 ).bits.length ]; 
		final long[] lightNormalized = new long[ normalized.length ];
		Arrays.fill( lightNormalized, -1L );

		while( ! equationQueue.isEmpty() ) {
			final int first = equationQueue.first(); // Index of the equation of minimum weight
			Modulo3Equation firstEquation = equations.get( first );
			if ( DEBUG ) System.err.println( "Looking at equation " + first + " of priority " + priority[ first ] + " : " + firstEquation );
			
			if ( priority[ first ] == 0 ) {
				equationQueue.dequeue();
				if ( firstEquation.isUnsolvable() ) return false;
				if ( firstEquation.isIdentity() ) continue;
				/* This equation must be necessarily solved by standard Gaussian elimination. No updated
				 * is needed, as all its variables are heavy. */
				dense.add( firstEquation );
			}
			else if ( priority[ first ] == 1 ) {
				equationQueue.dequeue();
				/* This is solved (in terms of the heavy variables). Let's find the pivot, that is, 
				 * the only light variable. Note that we do not need to update varEquation[] of any variable, as they
				 * are all either heavy (the non-pivot), or appearing only in this equation (the pivot). */
				firstEquation.normalized( normalized );
				for( int i = normalized.length; i-- != 0; ) normalized[ i ] &= lightNormalized[ i ];
				int wordIndex = 0;
				while( ( normalized[ wordIndex ] & lightNormalized[ wordIndex ] ) == 0 ) wordIndex++;
				final int pivot = wordIndex * 32 + Long.numberOfTrailingZeros( normalized[ wordIndex ] & lightNormalized[ wordIndex ] ) / 2; 

				// Record the light variable and the equation for computing it later.
				if ( DEBUG ) System.err.println( "Adding to solved variables x_" + pivot + " by equation " + firstEquation );
				pivots.add( pivot );
				solved.add( firstEquation );
				variableQueue.remove( pivot ); // Pivots cannot become heavy

				// Now we need to eliminate the variable from all other equations containing it.
				final int[] elements = varEquation[ pivot ].elements();
				for( int i = varEquation[ pivot ].size(); i-- != 0; ) {
					final int equationIndex = elements[ i ];
					if ( equationIndex == first ) continue;
					priority[ equationIndex ]--;
					equationQueue.changed( equationIndex );
					final Modulo3Equation equation = equations.get( equationIndex );
					if ( DEBUG ) System.err.print( "Replacing equation (" + equationIndex + ") " + equation + " with " );
					equation.eliminate( firstEquation, pivot );
					if ( DEBUG ) System.err.println( equation );
				}
			}
			else {
				// Make another variable heavy
				final int var = variableQueue.dequeue();
				isHeavy[ var ] = true;
				lightNormalized[ var / 32 ] ^= 1L << ( var % 32 ) * 2;
				if ( DEBUG ) {
					int numHeavy = 0;
					for( boolean b: isHeavy ) if ( b ) numHeavy++;
					System.err.println( "Making variable " + var + " of weight " + ( - weight[ var ] ) + " heavy (" + numHeavy + " heavy variables, " + equationQueue.size() + " equations to go)" );
				}
				final int[] elements = varEquation[ var ].elements();
				for( int i = varEquation[ var ].size(); i-- != 0; ) {
					final int equationIndex = elements[ i ];
					priority[ equationIndex ]--;
					equationQueue.changed( equationIndex );
				}
			}
		}


		if ( DEBUG ) {
			int numHeavy = 0;
			for( boolean b: isHeavy ) if ( b ) numHeavy++;
			System.err.println( "Heavy variables: " + numHeavy + " (" + Util.format( numHeavy * 100 / numVars ) + "%)" );
			System.err.println( "Dense equations: " + dense );
			System.err.println( "Solved equations: " + solved );
			System.err.println( "Pivots: " + pivots );
		}

		Modulo3System denseSystem = new Modulo3System( dense, numVars );
		if ( ! denseSystem.gaussianElimination( solution ) ) return false;  // numVars >= denseSystem.numVars

		final long[] solutionBits = solution.bits();
		final LongBigList solutionList = solution.asLongBigList( 2 );

		if ( DEBUG ) System.err.println( "Solution (dense): " + solutionList );

		for ( int i = solved.size(); i-- != 0; ) {
			final Modulo3Equation equation = solved.get( i );
			final int pivot = pivots.getInt( i );
			assert solutionList.getLong( pivot ) == 0 : pivot;

			final int pivotCoefficient = (int)equation.list.getLong( pivot );

			int sum = 0;
			
			final long[] bits = equation.bits;
			for( int j = solutionBits.length; j-- != 0; ) {
				final long high = bits[ j ] & 0xAAAAAAAAAAAAAAAAL;
				final long low = bits[ j ] & 0x5555555555555555L;
				final long highShift = high >>> 1; // Make every 10 into a 11 and zero everything else
				long t = ( solutionBits[ j ] ^ ( high | highShift ) ) & ( bits[ j ] | highShift | low << 1 ); // Exchange ones with twos, and make 00 into 11

				sum += Long.bitCount( t & 0xAAAAAAAAAAAAAAAAL ) * 2 + Long.bitCount( t & 0x5555555555555555L );
			}

			sum = ( equation.c - sum ) % 3;
			if ( sum < 0 ) sum += 3;

			assert pivotCoefficient != -1;
			solutionList.set( pivot,  sum == 0 ? 0 : pivotCoefficient == sum ? 1 : 2 );
		}

		if ( DEBUG ) System.err.println( "Solution (all): " + solutionList );

		return true;
	}

	public boolean gaussianElimination( final int[] solution ) {
		assert solution.length == numVars;
		LongArrayBitVector solutions = LongArrayBitVector.ofLength( numVars * 2 );
		if ( ! gaussianElimination( solutions ) ) return false;
		final LongBigList list = solutions.asLongBigList( 2 );
		for( int i = solution.length; i-- != 0; ) solution[ i ] = (int)list.getLong( i );
		return true;
	}

	public boolean gaussianElimination( final LongArrayBitVector solution ) {
		assert solution.length() == numVars * 2;
		if ( ! echelonForm() ) return false;
		final long[] solutionBits = solution.bits();
		final LongBigList solutionList = solution.asLongBigList( 2 );

		for ( int i = equations.size(); i-- != 0; ) {
			final Modulo3Equation equation = equations.get( i );
			if ( equation.isIdentity() ) continue;

			// First variable
			final int firstVar = equation.firstVar();
			final int firstCoeff = equation.coeff;
			assert solutionList.getLong( firstVar ) == 0 : firstVar;

			int sum = 0;

			final long[] bits = equation.bits;
			for( int j = solutionBits.length; j-- != 0; ) {
				final long high = bits[ j ] & 0xAAAAAAAAAAAAAAAAL;
				final long low = bits[ j ] & 0x5555555555555555L;
				final long highShift = high >>> 1; // Make every 10 into a 11 and zero everything else
				long t = ( solutionBits[ j ] ^ ( high | highShift ) ) & ( bits[ j ] | highShift | low << 1 ); // Exchange ones with twos, and make 00 into 11

				sum += Long.bitCount( t & 0xAAAAAAAAAAAAAAAAL ) * 2 + Long.bitCount( t & 0x5555555555555555L );
			}

			sum = ( equation.c - sum ) % 3;
			if ( sum < 0 ) sum += 3;

			solutionList.set( firstVar,  sum == 0 ? 0 : firstCoeff == sum ? 1 : 2 );
		}
		
		return true;
	}
	
	public int size() {
		return equations.size();
	}

	public boolean check( final int solution[] ) {
		for( Modulo3Equation equation: equations ) {
			int sum = 0;
			for( int var = equation.firstVar(); var != Integer.MAX_VALUE; var = equation.nextVar() ) 
				sum = ( sum + solution[ var ] * equation.coeff ) % 3;
			if ( equation.c != sum ) {
				// System.err.println( equation + " " + Arrays.toString( solution ) );
				return false;
			}
		}
		return true;
	}
}
