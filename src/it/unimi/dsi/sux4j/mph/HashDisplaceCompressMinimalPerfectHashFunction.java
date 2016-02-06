package it.unimi.dsi.sux4j.mph;

/*		 
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2002-2015 Sebastiano Vigna 
 *
 *  This library is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU Lesser General Public License as published by the Free
 *  Software Foundation; either version 3 of the License, or (at your option)
 *  any later version.
 *
 *  This library is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 *  or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 *  for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, see <http://www.gnu.org/licenses/>.
 *
 */

import it.unimi.dsi.Util;
import it.unimi.dsi.bits.Fast;
import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.bits.TransformationStrategies;
import it.unimi.dsi.bits.TransformationStrategy;
import it.unimi.dsi.fastutil.ints.AbstractIntComparator;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.longs.AbstractLongIterator;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongBigList;
import it.unimi.dsi.io.FastBufferedReader;
import it.unimi.dsi.io.FileLinesCollection;
import it.unimi.dsi.io.LineIterator;
import it.unimi.dsi.io.OfflineIterable;
import it.unimi.dsi.io.OfflineIterable.OfflineIterator;
import it.unimi.dsi.io.OfflineIterable.Serializer;
import it.unimi.dsi.lang.MutableString;
import it.unimi.dsi.logging.ProgressLogger;
import it.unimi.dsi.sux4j.bits.SparseRank;
import it.unimi.dsi.sux4j.io.ChunkedHashStore;
import it.unimi.dsi.sux4j.util.EliasFanoLongBigList;
import it.unimi.dsi.util.XorShift1024StarRandomGenerator;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.zip.GZIPInputStream;

import org.apache.commons.lang.mutable.MutableLong;
import org.apache.commons.math3.primes.Primes;
import org.apache.commons.math3.random.RandomGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.martiansoftware.jsap.FlaggedOption;
import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Parameter;
import com.martiansoftware.jsap.SimpleJSAP;
import com.martiansoftware.jsap.Switch;
import com.martiansoftware.jsap.UnflaggedOption;
import com.martiansoftware.jsap.stringparsers.FileStringParser;
import com.martiansoftware.jsap.stringparsers.ForNameStringParser;

/**
 * A minimal perfect hash function implemented using the &ldquo;hash, displace and compress&rdquo; technique.
 * 
 * <P>Given a list of keys without duplicates, the {@linkplain Builder builder} of this class finds a minimal
 * perfect hash function for the list. Subsequent calls to the {@link #getLong(Object)} method will
 * return a distinct number for each key in the list. For keys out of the list, the
 * resulting number is not specified. In some (rare) cases it might be possible to establish that a
 * key was not in the original list, and in that case -1 will be returned;
 * by <em>signing</em> the function (see below), you can guarantee with a prescribed probability
 * that -1 will be returned on keys not in the original list. The class can then be
 * saved by serialisation and reused later. 
 * 
 * <p>This class uses a {@linkplain ChunkedHashStore chunked hash store} to provide highly scalable construction. Note that at construction time
 * you can {@linkplain Builder#store(ChunkedHashStore) pass a ChunkedHashStore}
 * containing the keys (associated with any value); however, if the store is rebuilt because of a
 * {@link it.unimi.dsi.sux4j.io.ChunkedHashStore.DuplicateException} it will be rebuilt associating with each key its ordinal position.
 * 
 * <P>The memory requirements for the algorithm we use are &#8776;2 bits per key. Thus, this class
 * uses less memory than a {@link it.unimi.dsi.sux4j.mph.MinimalPerfectHashFunction}. Nonetheless,
 * its construction time is an order of magnitude larger. Query time is only slightly slower. Different
 * tradeoffs between construction time, query time and space can be obtained by tweaking the
 * {@linkplain Builder#loadFactor(int) load factor} and the parameter {@linkplain Builder#lambda(int) &lambda;} (see the
 * paper below for their exact meaning). 
 * 
 * <P>As a commodity, this class provides a main method that reads from standard input a (possibly
 * <code>gzip</code>'d) sequence of newline-separated strings, and writes a serialised minimal
 * perfect hash function for the given list.
 * 
 * <h3>Signing</h3>
 * 
 * <p>Optionally, it is possible to {@linkplain Builder#signed(int) <em>sign</em>} the minimal perfect hash function. A <var>w</var>-bit signature will
 * be associated with each key, so that {@link #getLong(Object)} will return -1 on strings that are not
 * in the original key set. As usual, false positives are possible with probability 2<sup>-<var>w</var></sup>.
 * 
 * <h3>How it Works</h3>
 * 
 * <p>The technique used is described by Djamal Belazzougui, Fabiano C. Botelho and Martin Dietzfelbinger
 * in &ldquo;Hash, displace and compress&rdquo;, <i>Algorithms - ESA 2009</i>, LNCS 5757, pages 682&minus;693, 2009.
 * However, with respect to the algorithm described in the paper, this implementation 
 * is much more scalable, as it uses a {@link ChunkedHashStore}
 * to split the generation of large key sets into generation of smaller functions for each chunk (of size
 * approximately 2<sup>{@value #LOG2_CHUNK_SIZE}</sup>).
 * 
 * @author Sebastiano Vigna
 * @since 3.2.0
 */

public class HashDisplaceCompressMinimalPerfectHashFunction<T> extends AbstractHashFunction<T> implements Serializable {
	private static final Logger LOGGER = LoggerFactory.getLogger( HashDisplaceCompressMinimalPerfectHashFunction.class );
	private static final boolean ASSERTS = true;

	public static final long serialVersionUID = 4L;

	/** A builder class for {@link HashDisplaceCompressMinimalPerfectHashFunction}. */
	public static class Builder<T> {
		protected Iterable<? extends T> keys;
		protected TransformationStrategy<? super T> transform;
		protected int signatureWidth;
		protected File tempDir;
		protected int lambda = 5;
		protected double loadFactor = 1;
		protected ChunkedHashStore<T> chunkedHashStore;
		/** Whether {@link #build()} has already been called. */
		protected boolean built;
		
		/** Specifies the keys to hash; if you have specified a {@link #store(ChunkedHashStore) ChunkedHashStore}, it can be {@code null}.
		 * 
		 * @param keys the keys to hash.
		 * @return this builder.
		 */
		public Builder<T> keys( final Iterable<? extends T> keys ) {
			this.keys = keys;
			return this;
		}
		
		/** Specifies the average size of a bucket.
		 * 
		 * @param lambda the average size of a bucket.
		 * @return this builder.
		 */
		public Builder<T> lambda( final int lambda ) {
			this.lambda = lambda;
			return this;
		}
		
		/** Specifies the load factor.
		 * 
		 * @param loadFactor the load factor.
		 * @return this builder.
		 */
		public Builder<T> loadFactor( final int loadFactor ) {
			this.loadFactor = loadFactor;
			return this;
		}
		
		/** Specifies the transformation strategy for the {@linkplain #keys(Iterable) keys to hash}.
		 * 
		 * @param transform a transformation strategy for the {@linkplain #keys(Iterable) keys to hash}.
		 * @return this builder.
		 */
		public Builder<T> transform( final TransformationStrategy<? super T> transform ) {
			this.transform = transform;
			return this;
		}
		
		/** Specifies that the resulting {@link HashDisplaceCompressMinimalPerfectHashFunction} should be signed using a given number of bits per key.
		 * 
		 * @param signatureWidth a signature width, or 0 for no signature.
		 * @return this builder.
		 */
		public Builder<T> signed( final int signatureWidth ) {
			this.signatureWidth = signatureWidth;
			return this;
		}
		
		/** Specifies a temporary directory for the {@link #store(ChunkedHashStore) ChunkedHashStore}.
		 * 
		 * @param tempDir a temporary directory for the {@link #store(ChunkedHashStore) ChunkedHashStore} files, or {@code null} for the standard temporary directory.
		 * @return this builder.
		 */
		public Builder<T> tempDir( final File tempDir ) {
			this.tempDir = tempDir;
			return this;
		}
		
		/** Specifies a chunked hash store containing the keys.
		 * 
		 * @param chunkedHashStore a chunked hash store containing the keys, or {@code null}; the store
		 * can be unchecked, but in this case you must specify {@linkplain #keys(Iterable) keys} and a {@linkplain #transform(TransformationStrategy) transform}
		 * (otherwise, in case of a hash collision in the store an {@link IllegalStateException} will be thrown). 
		 * @return this builder.
		 */
		public Builder<T> store( final ChunkedHashStore<T> chunkedHashStore ) {
			this.chunkedHashStore = chunkedHashStore;
			return this;
		}
		
		/** Builds a minimal perfect hash function.
		 * 
		 * @return a {@link HashDisplaceCompressMinimalPerfectHashFunction} instance with the specified parameters.
		 * @throws IllegalStateException if called more than once.
		 */
		public HashDisplaceCompressMinimalPerfectHashFunction<T> build() throws IOException {
			if ( built ) throw new IllegalStateException( "This builder has been already used" );
			built = true;
			if ( transform == null ) {
				if ( chunkedHashStore != null ) transform = chunkedHashStore.transform();
				else throw new IllegalArgumentException( "You must specify a TransformationStrategy, either explicitly or via a given ChunkedHashStore" );
			}
			return new HashDisplaceCompressMinimalPerfectHashFunction<T>( keys, transform, lambda, loadFactor, signatureWidth, tempDir, chunkedHashStore );
		}
	}
	
	/** The number of bits per block in the rank structure. */
	public static final int BITS_PER_BLOCK = 1024;

	/** The logarithm of the desired chunk size. */
	public final static int LOG2_CHUNK_SIZE = 16;

	/** The number of keys. */
	protected final long n;

	/** The shift for chunks. */
	private final int chunkShift;

	/** The seed used to generate the initial hash triple. */
	protected final long globalSeed;

	/** The seed of the underlying 3-hypergraphs. */
	protected final long[] seed;

	/** The start offset of each chunk. */
	protected final long[] offset;

	/** The number of buckets of each chunk, stored cumulatively. */
	protected final long[] numBuckets;

	/** The transformation strategy. */
	protected final TransformationStrategy<? super T> transform;
	
	/** The displacement coefficients. */
	protected final EliasFanoLongBigList coefficients;
	
	/** The sparse ranking structure containing the unused entries. */ 
	protected final SparseRank rank; 

	/** The mask to compare signatures, or zero for no signatures. */
	protected final long signatureMask;
	
	/** The signatures. */
	protected final LongBigList signatures;

	/**
	 * Creates a new minimal perfect hash function for the given keys.
	 * 
	 * @param keys the keys to hash, or {@code null}.
	 * @param transform a transformation strategy for the keys.
	 * @param lambda the average bucket size.
	 * @param loadFactor the load factor.
	 * @param signatureWidth a signature width, or 0 for no signature.
	 * @param tempDir a temporary directory for the store files, or {@code null} for the standard temporary directory.
	 * @param chunkedHashStore a chunked hash store containing the keys, or {@code null}; the store
	 * can be unchecked, but in this case <code>keys</code> and <code>transform</code> must be non-{@code null}. 
	 */
	protected HashDisplaceCompressMinimalPerfectHashFunction( final Iterable<? extends T> keys, final TransformationStrategy<? super T> transform, final int lambda, double loadFactor, final int signatureWidth, final File tempDir, ChunkedHashStore<T> chunkedHashStore ) throws IOException {
		this.transform = transform;

		final ProgressLogger pl = new ProgressLogger( LOGGER );
		pl.displayLocalSpeed = true;
		pl.displayFreeMemory = true;
		final RandomGenerator r = new XorShift1024StarRandomGenerator();
		pl.itemsName = "keys";

		final boolean givenChunkedHashStore = chunkedHashStore != null;
		if ( !givenChunkedHashStore ) {
			chunkedHashStore = new ChunkedHashStore<T>( transform, tempDir, pl );
			chunkedHashStore.reset( r.nextLong() );
			chunkedHashStore.addAll( keys.iterator() );
		}
		n = chunkedHashStore.size();

		defRetValue = -1; // For the very few cases in which we can decide

		int log2NumChunks = Math.max( 0, Fast.mostSignificantBit( n >> LOG2_CHUNK_SIZE ) );
		chunkShift = chunkedHashStore.log2Chunks( log2NumChunks );
		final int numChunks = 1 << log2NumChunks;

		LOGGER.info( "Number of chunks: " + numChunks );
		LOGGER.info( "Average chunk size: " + (double)n / numChunks );

		seed = new long[ numChunks ];
		offset = new long[ numChunks + 1 ];
		numBuckets = new long[ numChunks + 1 ];
		
		int duplicates = 0;
		final LongArrayList holes = new LongArrayList();
		
		@SuppressWarnings("resource")
		final OfflineIterable<MutableLong, MutableLong> coefficients = 
				new OfflineIterable<MutableLong, MutableLong>( new Serializer<MutableLong, MutableLong>() {

					@Override
					public void write( final MutableLong a, final DataOutput dos ) throws IOException {
						long x = a.longValue();
						while ( ( x & ~0x7FL ) != 0 ) {
							dos.writeByte( (int)( x | 0x80 ) );
							x >>>= 7;
						}
						dos.writeByte( (int)x );
					}

					@Override
					public void read( final DataInput dis, final MutableLong x ) throws IOException {
						byte b = dis.readByte();
						long t = b & 0x7F;
						for ( int shift = 7; ( b & 0x80 ) != 0; shift += 7 ) {
							b = dis.readByte();
							t |= ( b & 0x7FL ) << shift;
						}
						x.setValue( t );
					}
				}, new MutableLong() );
		
		for ( ;; ) {
			LOGGER.debug( "Generating minimal perfect hash function..." );

			holes.clear();
			coefficients.clear();
			pl.expectedUpdates = numChunks;
			pl.itemsName = "chunks";
			pl.start( "Analysing chunks... " );
			
			try {
				int chunkNumber = 0;
				
				for ( ChunkedHashStore.Chunk chunk : chunkedHashStore ) {
					/* We treat a chunk as a single hash function. The number of bins is thus
					 * the first prime larger than the chunk size divided by the load factor. */
					final int p = Primes.nextPrime( (int)Math.ceil( chunk.size() / loadFactor ) + 1 );
					final boolean used[] = new boolean[ p ];
	
					final int numBuckets = ( chunk.size() + lambda - 1 ) / lambda;
					this.numBuckets[ chunkNumber + 1 ] = this.numBuckets[ chunkNumber ] + numBuckets;
					final int[] cc0 = new int[ numBuckets ];
					final int[] cc1 = new int[ numBuckets ];
					@SuppressWarnings("unchecked")
					final ArrayList<long[]>[] bucket = new ArrayList[ numBuckets ];
					for( int i = bucket.length; i-- != 0; ) bucket[ i ] = new ArrayList<long[]>();
					
					tryChunk: for(;;) {
						for( ArrayList<long[]> b : bucket ) b.clear();
						Arrays.fill( used,  false );
						
						/* At each try, the allocation to keys to bucket is randomized differently. */
						final long seed = r.nextLong();
						// System.err.println( "Number of keys: " + chunk.size()  + " Number of bins: " + p + " seed: " + seed );
						/* We distribute the keys in this chunks in the buckets. */
						for( Iterator<long[]> iterator = chunk.iterator(); iterator.hasNext(); ) {
							final long[] triple = iterator.next();
							final long[] h = new long[ 3 ];
							Hashes.spooky( triple, seed, h );
							final ArrayList<long[]> b = bucket[ (int)( ( h[ 0 ] >>> 1 ) % numBuckets ) ];
							h[ 1 ] = (int)( ( h[ 1 ] >>> 1 ) % p ); 
							h[ 2 ] = (int)( ( h[ 2 ] >>> 1 ) % ( p - 1 ) ) + 1; 

							// All elements in a bucket must have either different h[ 1 ] or different h[ 2 ]
							for( long[] t: b ) if ( t[ 1 ] == h[ 1 ] && t[ 2 ] == h[ 2 ] ) {
								LOGGER.info( "Duplicate index" + Arrays.toString( t ) );
								continue tryChunk;
							}
							b.add( h );
						}

						final int[] perm = Util.identity( bucket.length );
						IntArrays.quickSort( perm, new AbstractIntComparator() {
							@Override
							public int compare( int a0, int a1 ) {
								return Integer.compare( bucket[ a1 ].size(), bucket[ a0 ].size() );
							}
						} );

						for( int i = 0; i < perm.length; ) {
							final LinkedList<Integer> bucketsToDo = new LinkedList<Integer>();
							final int size = bucket[ perm[ i ] ].size();
							//System.err.println( "Bucket size: " + size );
							int j;
							// Gather indices of all buckets with the same size
							for( j = i; j < perm.length && bucket[ perm[ j ] ].size() == size; j++ ) bucketsToDo.add( Integer.valueOf( perm[ j ] ) );

							// Examine for each pair (c0,c1) the buckets still to do
							ext: for( int c1 = 0; c1 < p; c1++ )
								for( int c0 = 0; c0 < p; c0++ )  {
									//System.err.println( "Testing " + c0 + ", " + c1 + " (to do: " + bucketsToDo.size() + ")" );
									for( Iterator<Integer> iterator = bucketsToDo.iterator(); iterator.hasNext(); ) {
										final int k = iterator.next().intValue();
										final ArrayList<long[]> b = bucket[ k ];
										boolean completed = true;
										final IntArrayList done = new IntArrayList();
										// Try to see whether the necessary entries are not used
										for( long[] h: b ) {
											//assert k == h[ 0 ];

											int pos = (int)( ( h[ 1 ] + c0 * h[ 2 ] + c1 ) % p );
											//System.err.println( "Testing pos " + pos + " for " + Arrays.toString( e  ));
											if ( used[ pos ] ) {
												completed = false;
												break;
											}
											else {
												used[ pos ] = true;
												done.add( pos );
											}
										}

										if ( completed ) {
											// All positions were free
											cc0[ k ] = c0;
											cc1[ k ] = c1;
											iterator.remove();
										}
										else for( int d: done ) used[ d ] = false;
									}
									if ( bucketsToDo.isEmpty() ) break ext;
								}
							if ( ! bucketsToDo.isEmpty() ) continue tryChunk;
							
							this.seed[ chunkNumber ] = seed;							
							i = j;
						}
						break;
					}

					// System.err.println("DONE!");
					
					if ( ASSERTS ) {
						final IntOpenHashSet pos = new IntOpenHashSet();
						final long h[] = new long[ 3 ];
						for( Iterator<long[]> iterator = chunk.iterator(); iterator.hasNext(); ) {
							final long[] triple = iterator.next();
							Hashes.spooky( triple, seed[ chunkNumber ], h );
							h[ 0 ] = ( h[ 0 ] >>> 1 ) % numBuckets;
							h[ 1 ] = (int)( ( h[ 1 ] >>> 1 ) % p ); 
							h[ 2 ] = (int)( ( h[ 2 ] >>> 1 ) % ( p - 1 ) ) + 1; 
							//System.err.println( Arrays.toString(  e  ) );
							assert pos.add( (int)( ( h[ 1 ] + cc0[ (int)( h[ 0 ] ) ] * h[ 2 ] + cc1[ (int)( h[ 0 ] ) ] ) % p ) );
						}
					}
					
					final MutableLong l = new MutableLong();
					for( int i = 0; i < numBuckets; i++ ) {
						l.setValue( cc0[ i ] + cc1[ i ] * p );
						coefficients.add( l );
					}
					
					for( int i = 0; i < p; i++ ) if ( ! used[ i ] ) holes.add( offset[ chunkNumber ] + i );

					offset[ chunkNumber + 1 ] = offset[ chunkNumber ] + p;
					chunkNumber++;
					pl.update();
				}

				pl.done();
				break;
			}
			catch ( ChunkedHashStore.DuplicateException e ) {
				if ( keys == null ) throw new IllegalStateException( "You provided no keys, but the chunked hash store was not checked" );
				if ( duplicates++ > 3 ) throw new IllegalArgumentException( "The input list contains duplicates" );
				LOGGER.warn( "Found duplicate. Recomputing triples..." );
				chunkedHashStore.reset( r.nextLong() );
				chunkedHashStore.addAll( keys.iterator() );
			}
		}

		rank = new SparseRank( offset[ offset.length - 1 ], holes.size(), holes.iterator() );

		globalSeed = chunkedHashStore.seed();

		this.coefficients = new EliasFanoLongBigList( new AbstractLongIterator() {
			final OfflineIterator<MutableLong, MutableLong> iterator = coefficients.iterator();
			
			@Override
			public boolean hasNext() {
				return iterator.hasNext();
			}
			
			public long nextLong() {
				return iterator.next().longValue();
			}
		}, 0 );
		
		coefficients.close();

		LOGGER.info( "Completed." );
		LOGGER.info( "Actual bit cost per key: " + (double)numBits() / n );

		if ( signatureWidth != 0 ) {
			signatureMask = -1L >>> Long.SIZE - signatureWidth;
			( signatures = LongArrayBitVector.getInstance().asLongBigList( signatureWidth ) ).size( n );
			pl.expectedUpdates = n;
			pl.itemsName = "signatures";
			pl.start( "Signing..." );
			for ( ChunkedHashStore.Chunk chunk : chunkedHashStore ) {
				Iterator<long[]> iterator = chunk.iterator();
				for( int i = chunk.size(); i-- != 0; ) { 
					final long[] triple = iterator.next();
					long t = getLongByTripleNoCheck( triple );
					signatures.set( t, signatureMask & triple[ 0 ] );
					pl.lightUpdate();
				}
			}
			pl.done();
		}
		else {
			signatureMask = 0;
			signatures = null;
		}

		if ( !givenChunkedHashStore ) chunkedHashStore.close();
	}

	/**
	 * Returns the number of bits used by this structure.
	 * 
	 * @return the number of bits used by this structure.
	 */
	public long numBits() {
		return seed.length * Long.SIZE + offset.length * Long.SIZE + coefficients.numBits() + numBuckets.length * Long.SIZE + rank.numBits();
	}

	@SuppressWarnings("unchecked")
	public long getLong( final Object key ) {
		if ( n == 0 ) return defRetValue;
		final long[] triple = new long[ 3 ];
		Hashes.spooky4( transform.toBitVector( (T)key ), globalSeed, triple );
		final int chunk = chunkShift == Long.SIZE ? 0 : (int)( triple[ 0 ] >>> chunkShift );
		final long chunkOffset = offset[ chunk ];
		final int p = (int)( offset[ chunk + 1 ] - chunkOffset );

		final long[] h = new long[ 3 ];
		Hashes.spooky( triple, seed[ chunk ], h );
		h[ 1 ] = (int)( ( h[ 1 ] >>> 1 ) % p ); 
		h[ 2 ] = (int)( ( h[ 2 ] >>> 1 ) % ( p - 1 ) ) + 1; 
		
		final long c = coefficients.getLong( numBuckets[ chunk ] + ( h[ 0 ] >>> 1 ) % ( numBuckets[ chunk + 1 ] - numBuckets[ chunk ] ) );
		
		long result = chunkOffset + (int)( ( h[ 1 ] + ( c % p ) * h[ 2 ] + c / p ) % p );
		result -= rank.rank( result );

		if ( signatureMask != 0 ) return result >= n || ( ( signatures.getLong( result ) ^ triple[ 0 ] ) & signatureMask ) != 0 ? defRetValue : result;
		// Out-of-set strings can generate bizarre 3-hyperedges.
		return result < n ? result : defRetValue;
	}

	/** A dirty function replicating the behaviour of {@link #getLongByTriple(long[])} but skipping the
	 * signature test. Used in the constructor. <strong>Must</strong> be kept in sync with {@link #getLongByTriple(long[])}. */ 
	private long getLongByTripleNoCheck( final long[] triple ) {
		final int chunk = chunkShift == Long.SIZE ? 0 : (int)( triple[ 0 ] >>> chunkShift );
		final long chunkOffset = offset[ chunk ];
		final int p = (int)( offset[ chunk + 1 ] - chunkOffset );
		
		final long[] h = new long[ 3 ];
		Hashes.spooky( triple, seed[ chunk ], h );
		h[ 1 ] = (int)( ( h[ 1 ] >>> 1 ) % p ); 
		h[ 2 ] = (int)( ( h[ 2 ] >>> 1 ) % ( p - 1 ) ) + 1; 
		
		final long c = coefficients.getLong( numBuckets[ chunk ] + ( h[ 0 ] >>> 1 ) % ( numBuckets[ chunk + 1 ] - numBuckets[ chunk ] ) );

		final long result = chunkOffset + (int)( ( h[ 1 ] + ( c % p ) * h[ 2 ] + c / p ) % p );
		return result - rank.rank( result );
	}

	public long size64() {
		return n;
	}

	private void readObject( final ObjectInputStream s ) throws IOException, ClassNotFoundException {
		s.defaultReadObject();
	}


	public static void main( final String[] arg ) throws NoSuchMethodException, IOException, JSAPException {

		final SimpleJSAP jsap = new SimpleJSAP( HashDisplaceCompressMinimalPerfectHashFunction.class.getName(), "Builds a minimal perfect hash function reading a newline-separated list of strings.", new Parameter[] {
				new FlaggedOption( "encoding", ForNameStringParser.getParser( Charset.class ), "UTF-8", JSAP.NOT_REQUIRED, 'e', "encoding", "The string file encoding." ),
				new FlaggedOption( "tempDir", FileStringParser.getParser(), JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, 'T', "temp-dir", "A directory for temporary files." ),
				new Switch( "iso", 'i', "iso", "Use ISO-8859-1 coding internally (i.e., just use the lower eight bits of each character)." ),
				new Switch( "utf32", JSAP.NO_SHORTFLAG, "utf-32", "Use UTF-32 internally (handles surrogate pairs)." ),
				new FlaggedOption( "lambda", JSAP.INTEGER_PARSER, "5", JSAP.NOT_REQUIRED, 'l', "lambda", "The average size of a bucket of the first-level hash function." ),
				new FlaggedOption( "loadFactor", JSAP.DOUBLE_PARSER, "1", JSAP.NOT_REQUIRED, 'f', "load-factor", "The load factor." ),
				new FlaggedOption( "signatureWidth", JSAP.INTEGER_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, 's', "signature-width", "If specified, the signature width in bits." ),
				new Switch( "zipped", 'z', "zipped", "The string list is compressed in gzip format." ),
				new UnflaggedOption( "function", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The filename for the serialised minimal perfect hash function." ),
				new UnflaggedOption( "stringFile", JSAP.STRING_PARSER, "-", JSAP.NOT_REQUIRED, JSAP.NOT_GREEDY,
						"The name of a file containing a newline-separated list of strings, or - for standard input; in the first case, strings will not be loaded into core memory." ), } );

		JSAPResult jsapResult = jsap.parse( arg );
		if ( jsap.messagePrinted() ) return;

		final String functionName = jsapResult.getString( "function" );
		final String stringFile = jsapResult.getString( "stringFile" );
		final Charset encoding = (Charset)jsapResult.getObject( "encoding" );
		final boolean zipped = jsapResult.getBoolean( "zipped" );
		final boolean iso = jsapResult.getBoolean( "iso" );
		final boolean utf32 = jsapResult.getBoolean( "utf32" );
		final int signatureWidth = jsapResult.getInt( "signatureWidth", 0 ); 
		final int lambda = jsapResult.getInt( "lambda" ); 
		final double loadFactor = jsapResult.getDouble( "loadFactor" ); 

		final Collection<MutableString> collection;
		if ( "-".equals( stringFile ) ) {
			final ProgressLogger pl = new ProgressLogger( LOGGER );
			pl.displayLocalSpeed = true;
			pl.displayFreeMemory = true;
			pl.start( "Loading strings..." );
			collection = new LineIterator( new FastBufferedReader( new InputStreamReader( zipped ? new GZIPInputStream( System.in ) : System.in, encoding ) ), pl ).allLines();
			pl.done();
		}
		else collection = new FileLinesCollection( stringFile, encoding.toString(), zipped );
		final TransformationStrategy<CharSequence> transformationStrategy = iso 
				? TransformationStrategies.iso() 
				: utf32 
						? TransformationStrategies.utf32()
						: TransformationStrategies.utf16();

		BinIO.storeObject( new HashDisplaceCompressMinimalPerfectHashFunction<CharSequence>( collection, transformationStrategy, lambda, loadFactor, signatureWidth, jsapResult.getFile( "tempDir"), null ), functionName );
		LOGGER.info( "Saved." );
	}
}
